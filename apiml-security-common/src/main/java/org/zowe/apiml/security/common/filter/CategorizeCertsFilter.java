/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.security.common.filter;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.zowe.apiml.security.common.verify.CertificateValidator;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * This filter processes certificates on request. It decides, which certificates are considered for client authentication
 */
@RequiredArgsConstructor
@Slf4j
@Component
public class CategorizeCertsFilter extends OncePerRequestFilter {

    private static final String ATTRNAME_CLIENT_AUTH_X509_CERTIFICATE = "client.auth.X509Certificate";
    private static final String ATTRNAME_JAVAX_SERVLET_REQUEST_X509_CERTIFICATE = "javax.servlet.request.X509Certificate";
    private static final String LOG_FORMAT_FILTERING_CERTIFICATES = "Filtering certificates: {} -> {}";
    private static final String X_AUTH_SOURCE = "x-auth-source";
    private static final String X_AUTH_SIGNATURE = "x-auth-signature";
    @Value("${apiml.security.x509.authViaHeader:false}")
    private boolean x509AuthViaHeader;
    private final Set<String> publicKeyCertificatesBase64;
    private final CertificateValidator certificateValidator;

    public Set<String> getPublicKeyCertificatesBase64() {
        return publicKeyCertificatesBase64;
    }

    private X509Certificate[] getX509Certificates(X509Certificate[] certs, String certFromHeader, String certSignature) throws SignatureException, CertificateException, IOException {
        byte[] decodedCertData = Base64.getDecoder().decode(certFromHeader);
        byte[] decodedSignatureData = Base64.getDecoder().decode(certSignature);
        boolean certificateIntegrity = certificateValidator.verify(decodedCertData, decodedSignatureData);
        if (certificateIntegrity) {
            if (certs == null) {
                certs = new X509Certificate[0];
            }
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            InputStream certStream = new ByteArrayInputStream(decodedCertData);
            X509Certificate certificate = (X509Certificate) cf.generateCertificate(certStream);
            certStream.close();
            certs = Arrays.stream(certs)
                .map(cert -> cert.equals(certificate) ? certificate : cert)
                .toArray(X509Certificate[]::new);
            certs = Arrays.copyOf(certs, certs.length + 1);
            certs[certs.length - 1] = certificate;
            return certs;
        }
        return certs;
    }

    /**
     * Get certificates from request (if exists), separate them (to use only APIML certificate to request sign and
     * other for authentication) and store again into request.
     * If authentication via certificate in header is enabled, get certificate from a custom authentication header,
     * decrypt it to validate its authenticity using the public key and store it in the request.
     * @param request Request to filter certificates
     */
    private void categorizeCerts(ServletRequest request) {
        X509Certificate[] certs = (X509Certificate[]) request.getAttribute(ATTRNAME_JAVAX_SERVLET_REQUEST_X509_CERTIFICATE);
        if (x509AuthViaHeader) {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            String certFromHeader = httpRequest.getHeader(X_AUTH_SOURCE);
            String certSignature = httpRequest.getHeader(X_AUTH_SIGNATURE);
            if (certFromHeader != null && certSignature != null && !certFromHeader.isEmpty() && !certSignature.isEmpty()) {
                try {
                    certs = getX509Certificates(certs, certFromHeader, certSignature);
                } catch (CertificateException | IOException | SignatureException e) {
                    log.error("Cannot extract X509 certificate from the authentication header {}", X_AUTH_SOURCE, e);
                }
            }
        }
        if (certs != null) {
            request.setAttribute(ATTRNAME_CLIENT_AUTH_X509_CERTIFICATE, selectCerts(certs, certificateForClientAuth));
            request.setAttribute(ATTRNAME_JAVAX_SERVLET_REQUEST_X509_CERTIFICATE, selectCerts(certs, notCertificateForClientAuth));
            log.debug(LOG_FORMAT_FILTERING_CERTIFICATES, ATTRNAME_CLIENT_AUTH_X509_CERTIFICATE, request.getAttribute(ATTRNAME_CLIENT_AUTH_X509_CERTIFICATE));
        }
    }

    /**
     * This filter removes all certificates in attribute "javax.servlet.request.X509Certificate" which has no relations
     * with private certificate of apiml and then call original implementation (without "foreign" certificates)
     *
     * @param request     request to process
     * @param response    response of call
     * @param filterChain chain of filters to evaluate
     **/
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        categorizeCerts(request);
        filterChain.doFilter(request, response);
    }

    private X509Certificate[] selectCerts(X509Certificate[] certs, Predicate<X509Certificate> test) {
        return Arrays.stream(certs)
            .filter(test)
            .collect(Collectors.toList()).toArray(new X509Certificate[0]);
    }

    public String base64EncodePublicKey(X509Certificate cert) {
        return Base64.getEncoder().encodeToString(cert.getPublicKey().getEncoded());
    }

    public void setCertificateForClientAuth(Predicate<X509Certificate> certificateForClientAuth) {
        this.certificateForClientAuth = certificateForClientAuth;
    }

    public void setNotCertificateForClientAuth(Predicate<X509Certificate> notCertificateForClientAuth) {
        this.notCertificateForClientAuth = notCertificateForClientAuth;
    }

    Predicate<X509Certificate> certificateForClientAuth = crt -> !getPublicKeyCertificatesBase64().contains(base64EncodePublicKey(crt));
    Predicate<X509Certificate> notCertificateForClientAuth = crt -> getPublicKeyCertificatesBase64().contains(base64EncodePublicKey(crt));


}
