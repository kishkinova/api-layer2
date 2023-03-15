/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.zaasclient.service.internal;

import lombok.AllArgsConstructor;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.zowe.apiml.zaasclient.config.ConfigProperties;
import org.zowe.apiml.zaasclient.exception.ZaasConfigurationErrorCodes;
import org.zowe.apiml.zaasclient.exception.ZaasConfigurationException;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@AllArgsConstructor
class ZaasHttpsClientProvider implements CloseableClientProvider {
    private static final int REQUEST_TIMEOUT = 30 * 1000;

    private final RequestConfig requestConfig;

    private static final Pattern KEYRING_PATTERN = Pattern.compile("^(safkeyring[^:]*):/{2,4}([^/]+)/([^/]+)$");

    private TrustManagerFactory tmf;
    private KeyManagerFactory kmf;

    private final char[] keyStorePassword;
    private final String keyStoreType;
    private final String keyStorePath;
    private final HostnameVerifier hostnameVerifier;

    private final CookieStore cookieStore = new BasicCookieStore();

    private CloseableHttpClient httpsClient;

    public ZaasHttpsClientProvider(ConfigProperties configProperties) throws ZaasConfigurationException {
        this.requestConfig = this.buildCustomRequestConfig();

        if (configProperties.getTrustStorePath() == null) {
            throw new ZaasConfigurationException(ZaasConfigurationErrorCodes.TRUST_STORE_NOT_PROVIDED);
        }

        initializeTrustManagerFactory(configProperties.getTrustStorePath(), configProperties.getTrustStoreType(), configProperties.getTrustStorePassword());
        this.hostnameVerifier = configProperties.isNonStrictVerifySslCertificatesOfServices() ? new NoopHostnameVerifier() : SSLConnectionSocketFactory.getDefaultHostnameVerifier();
        this.keyStorePath = configProperties.getKeyStorePath();
        this.keyStorePassword = configProperties.getKeyStorePassword();
        this.keyStoreType = configProperties.getKeyStoreType();
    }

    static boolean isKeyring(String input) {
        if (input == null) return false;
        Matcher matcher = KEYRING_PATTERN.matcher(input);
        return matcher.matches();
    }

    static String formatKeyringUrl(String input) {
        if (input == null) return null;
        Matcher matcher = KEYRING_PATTERN.matcher(input);
        if (matcher.matches()) {
            return matcher.group(1) + "://" + matcher.group(2) + "/" + matcher.group(3);
        }
        return input;
    }

    public void clearCookieStore() {
        this.cookieStore.clear();
    }

    @Override
    public synchronized CloseableHttpClient getHttpClient() throws ZaasConfigurationException {
        if (httpsClient == null) {
            if (kmf == null) {
                initializeKeyStoreManagerFactory();
            }
            httpsClient = sharedHttpClientConfiguration(getSSLContext()).build();
        }
        return httpsClient;
    }

    private void initializeTrustManagerFactory(String trustStorePath, String trustStoreType, char[] trustStorePassword)
        throws ZaasConfigurationException {
        try {
            tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            KeyStore trustStore = getKeystore(trustStorePath, trustStoreType, trustStorePassword);

            tmf.init(trustStore);
        } catch (CertificateException | NoSuchAlgorithmException | KeyStoreException e) {
            throw new ZaasConfigurationException(ZaasConfigurationErrorCodes.WRONG_CRYPTO_CONFIGURATION, e);
        } catch (IOException e) {
            throw new ZaasConfigurationException(ZaasConfigurationErrorCodes.IO_CONFIGURATION_ISSUE, e);
        }
    }

    private void initializeKeyStoreManagerFactory() throws ZaasConfigurationException {
        try {
            KeyStore keyStore;
            if (keyStorePath != null) {
                keyStore = getKeystore(keyStorePath, keyStoreType, keyStorePassword);
            } else {
                keyStore = getEmptyKeystore();
            }

            kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, keyStorePassword);
        } catch (NoSuchAlgorithmException | CertificateException | UnrecoverableKeyException | KeyStoreException e) {
            throw new ZaasConfigurationException(ZaasConfigurationErrorCodes.WRONG_CRYPTO_CONFIGURATION, e);
        } catch (IOException e) {
            throw new ZaasConfigurationException(ZaasConfigurationErrorCodes.IO_CONFIGURATION_ISSUE, e);
        }
    }

    private KeyStore getKeystore(String uri, String keyStoreType, char[] storePassword) throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
        KeyStore keyStore = KeyStore.getInstance(keyStoreType);
        try (InputStream correctInStream = getCorrectInputStream(uri)) {
            keyStore.load(correctInStream, storePassword);
            return keyStore;
        }
    }

    // Necessary because IBM JDK will automatically add keyStore based on system variables when there is no keyStore
    private KeyStore getEmptyKeystore() throws KeyStoreException, CertificateException, IOException, NoSuchAlgorithmException {
        KeyStore emptyKeystore = KeyStore.getInstance(KeyStore.getDefaultType());
        emptyKeystore.load(null, null);

        return emptyKeystore;
    }

    private InputStream getCorrectInputStream(String uri) throws IOException {
        if (isKeyring(uri)) {
            URL url = new URL(formatKeyringUrl(uri));
            return url.openStream();
        }
        return new FileInputStream(uri);
    }

    private SSLContext getSSLContext() throws ZaasConfigurationException {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(
                kmf != null ? kmf.getKeyManagers() : null,
                tmf.getTrustManagers(),
                new SecureRandom()
            );
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(hostnameVerifier);
            return sslContext;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new ZaasConfigurationException(ZaasConfigurationErrorCodes.WRONG_CRYPTO_CONFIGURATION, e);
        }
    }

    /**
     * Create Http Configuration with defaults for maximum of connections and maximum of connections per route.
     */
    private HttpClientBuilder sharedHttpClientConfiguration(SSLContext sslContext) {

        final SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext,
            hostnameVerifier);

        return HttpClients.custom()
            .setSSLSocketFactory(sslsf)
            .setDefaultRequestConfig(this.requestConfig)
            .setMaxConnTotal(3 * 3)
            .setMaxConnPerRoute(3)
            .setDefaultCookieStore(cookieStore);
    }

    private RequestConfig buildCustomRequestConfig() {
        final RequestConfig.Builder builder = RequestConfig.custom();
        builder.setConnectionRequestTimeout(REQUEST_TIMEOUT);
        builder.setSocketTimeout(REQUEST_TIMEOUT);
        builder.setConnectTimeout(REQUEST_TIMEOUT);
        return builder.build();
    }
}
