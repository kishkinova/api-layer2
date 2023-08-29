/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.security.common.verify;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.zowe.apiml.message.log.ApimlLogger;
import org.zowe.apiml.product.logging.annotations.InjectApimlLogger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.*;

@Service
@Slf4j
public class TrustedCertificatesProvider {

    private final Set<String> publicKeyCertificatesBase64;
    private final CloseableHttpClient httpClient;

    @InjectApimlLogger
    private final ApimlLogger apimlLog = ApimlLogger.empty();

    @Autowired
    public TrustedCertificatesProvider(@Qualifier("secureHttpClientWithoutKeystore") CloseableHttpClient httpClient,
                                       @Qualifier("publicKeyCertificatesBase64") Set<String> publicKeyCertificatesBase64) {
        this.httpClient = httpClient;
        this.publicKeyCertificatesBase64 = publicKeyCertificatesBase64;
    }

    /**
     * Query given rest endpoint to get the certificate chain from remote proxy gateway.
     * The endpoint should be publicly available and should provide the certificate chain in PEM format.
     *
     * @param certificatesEndpoint Given full URL to the remote proxy gateway certificates endpoint
     * @return List of certificates or empty list
     */
    @Cacheable(value = "trustedCertificates", key = "#certificatesEndpoint", unless = "#result.isEmpty()")
    public List<Certificate> getTrustedCerts(String certificatesEndpoint) {
        List<Certificate> trustedCerts = new ArrayList<>();
        String pem = callCertificatesEndpoint(certificatesEndpoint);
        if (StringUtils.isNotEmpty(pem)) {
            try {
                Collection<? extends Certificate> certs = CertificateFactory
                    .getInstance("X.509")
                    .generateCertificates(new ByteArrayInputStream(pem.getBytes()));
                trustedCerts.addAll(certs);
                updateTrustedPublicKeys(trustedCerts);
            } catch (CertificateException e) {
                apimlLog.log("org.zowe.apiml.security.common.verify.errorParsingCertificates", e.getMessage());
            }
        }
        return trustedCerts;
    }

    private String callCertificatesEndpoint(String url) {
        try {
            HttpGet httpGet = new HttpGet(new URI(url));
            HttpResponse httpResponse = httpClient.execute(httpGet);
            final int statusCode = httpResponse.getStatusLine() != null ? httpResponse.getStatusLine().getStatusCode() : 0;
            String body = "";
            if (httpResponse.getEntity() != null) {
                body = EntityUtils.toString(httpResponse.getEntity(), StandardCharsets.UTF_8);
            }
            if (statusCode != HttpStatus.SC_OK) {
                apimlLog.log("org.zowe.apiml.security.common.verify.invalidResponse", url, statusCode, body);
                return null;
            }
            log.debug("Trusted certificates from {}: {}", url, body);
            return body;

        } catch (URISyntaxException e) {
            apimlLog.log("org.zowe.apiml.security.common.verify.invalidURL", e.getMessage());
        } catch (IOException e) {
            apimlLog.log("org.zowe.apiml.security.common.verify.httpError", e.getMessage());
        }
        return null;
    }

    /**
     * Updates the list of public keys of APIML trusted certificates
     *
     * @param certs List of trusted certificates
     */
    private void updateTrustedPublicKeys(List<Certificate> certs) {
        for (Certificate cert : certs) {
            String publicKey = Base64.getEncoder().encodeToString(cert.getPublicKey().getEncoded());
            publicKeyCertificatesBase64.add(publicKey);
        }
    }
}