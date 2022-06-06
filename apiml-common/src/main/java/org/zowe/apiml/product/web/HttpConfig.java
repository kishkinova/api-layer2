/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.product.web;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.netflix.discovery.AbstractDiscoveryClientOptionalArgs;
import com.netflix.discovery.shared.transport.jersey.EurekaJerseyClient;
import com.netflix.discovery.shared.transport.jersey.EurekaJerseyClientImpl.EurekaJerseyClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;
import org.zowe.apiml.message.log.ApimlLogger;
import org.zowe.apiml.product.logging.annotations.InjectApimlLogger;
import org.zowe.apiml.security.HttpsConfig;
import org.zowe.apiml.security.HttpsConfigError;
import org.zowe.apiml.security.HttpsFactory;
import org.zowe.apiml.security.SecurityUtils;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.util.Set;
import java.util.function.Supplier;

@Slf4j
@Configuration
public class HttpConfig {
    @Value("${server.ssl.protocol:TLSv1.2}")
    private String protocol;

    @Value("${server.ssl.trustStore:#{null}}")
    private String trustStore;

    @Value("${server.ssl.trustStorePassword:#{null}}")
    private char[] trustStorePassword;

    @Value("${server.ssl.trustStoreType:PKCS12}")
    private String trustStoreType;

    @Value("${server.ssl.keyAlias:#{null}}")
    private String keyAlias;

    @Value("${server.ssl.keyStore:#{null}}")
    private String keyStore;

    @Value("${server.ssl.keyStorePassword:#{null}}")
    private char[] keyStorePassword;

    @Value("${server.ssl.keyPassword:#{null}}")
    private char[] keyPassword;

    @Value("${server.ssl.keyStoreType:PKCS12}")
    private String keyStoreType;

    @Value("${server.ssl.ciphers:.*}")
    private String[] ciphers;

    @Value("${apiml.security.ssl.verifySslCertificatesOfServices:true}")
    private boolean verifySslCertificatesOfServices;

    @Value("${apiml.security.ssl.nonStrictVerifySslCertificatesOfServices:false}")
    private boolean nonStrictVerifySslCertificatesOfServices;

    @Value("${spring.application.name}")
    private String serviceId;

    @Value("${server.ssl.trustStoreRequired:false}")
    private boolean trustStoreRequired;

    @Value("${eureka.client.serviceUrl.defaultZone}")
    private String eurekaServerUrl;

    @Value("${server.maxConnectionsPerRoute:#{10}}")
    private Integer maxConnectionsPerRoute;

    @Value("${server.maxTotalConnections:#{100}}")
    private Integer maxTotalConnections;

    @Value("${apiml.httpclient.conn-pool.idleConnTimeoutSeconds:#{5}}")
    private int idleConnTimeoutSeconds;
    @Value("${apiml.httpclient.conn-pool.requestConnectionTimeout:#{10000}}")
    private int requestConnectionTimeout;
    @Value("${apiml.httpclient.conn-pool.readTimeout:#{10000}}")
    private int readTimeout;
    @Value("${apiml.httpclient.conn-pool.timeToLive:#{10000}}")
    private int timeToLive;

    @Value("${server.attls.enabled:false}")
    private boolean isAttlsEnabled;

    private CloseableHttpClient secureHttpClient;
    private CloseableHttpClient secureHttpClientWithoutKeystore;
    private SSLContext secureSslContext;
    private HostnameVerifier secureHostnameVerifier;
    private EurekaJerseyClientBuilder eurekaJerseyClientBuilder;

    @InjectApimlLogger
    private ApimlLogger apimlLog = ApimlLogger.empty();

    private Set<String> publicKeyCertificatesBase64;

    @Resource
    private AbstractDiscoveryClientOptionalArgs<?> optionalArgs;


    @PostConstruct
    public void init() {
        try {
            Supplier<HttpsConfig.HttpsConfigBuilder> httpsConfigSupplier = () ->
                HttpsConfig.builder()
                    .protocol(protocol)
                    .trustStore(trustStore).trustStoreType(trustStoreType)
                    .trustStorePassword(trustStorePassword).trustStoreRequired(trustStoreRequired)
                    .verifySslCertificatesOfServices(verifySslCertificatesOfServices)
                    .nonStrictVerifySslCertificatesOfServices(nonStrictVerifySslCertificatesOfServices)
                    .maxConnectionsPerRoute(maxConnectionsPerRoute).maxTotalConnections(maxTotalConnections)
                    .idleConnTimeoutSeconds(idleConnTimeoutSeconds).requestConnectionTimeout(requestConnectionTimeout)
                    .timeToLive(timeToLive);

            HttpsConfig httpsConfig = httpsConfigSupplier.get()
                .keyAlias(keyAlias).keyStore(keyStore).keyPassword(keyPassword)
                .keyStorePassword(keyStorePassword).keyStoreType(keyStoreType).trustStore(trustStore)
                .build();

            HttpsConfig httpsConfigWithoutKeystore = httpsConfigSupplier.get().build();

            log.info("Using HTTPS configuration: {}", httpsConfig.toString());

            HttpsFactory factory = new HttpsFactory(httpsConfig);
            secureHttpClient = factory.createSecureHttpClient();
            secureSslContext = factory.createSslContext();
            secureHostnameVerifier = factory.createHostnameVerifier();
            eurekaJerseyClientBuilder = factory.createEurekaJerseyClientBuilder(eurekaServerUrl, serviceId);
            optionalArgs.setEurekaJerseyClient(eurekaJerseyClient());
            HttpsFactory factoryWithoutKeystore = new HttpsFactory(httpsConfigWithoutKeystore);
            secureHttpClientWithoutKeystore = factoryWithoutKeystore.createSecureHttpClient();

            factory.setSystemSslProperties();

            publicKeyCertificatesBase64 = SecurityUtils.loadCertificateChainBase64(httpsConfig);

        } catch (HttpsConfigError e) {
            System.exit(1); // NOSONAR
        } catch (Exception e) {
            apimlLog.log("org.zowe.apiml.common.unknownHttpsConfigError", e.getMessage());
            System.exit(1); // NOSONAR
        }
    }

    @Bean
    @Qualifier("publicKeyCertificatesBase64")
    public Set<String> publicKeyCertificatesBase64() {
        return publicKeyCertificatesBase64;
    }

    private void setTruststore(SslContextFactory sslContextFactory) {
        if (StringUtils.isNotEmpty(trustStore)) {
            sslContextFactory.setTrustStorePath(SecurityUtils.replaceFourSlashes(trustStore));
            sslContextFactory.setTrustStoreType(trustStoreType);
            sslContextFactory.setTrustStorePassword(trustStorePassword == null ? null : String.valueOf(trustStorePassword));
        }
    }

    @Bean
    @Qualifier("jettyClientSslContextFactory")
    public SslContextFactory.Client jettyClientSslContextFactory() {
        SslContextFactory.Client sslContextFactory = new SslContextFactory.Client();
        sslContextFactory.setProtocol(protocol);
        sslContextFactory.setExcludeCipherSuites("^.*_(MD5|SHA|SHA1)$", "^TLS_RSA_.*$");
        setTruststore(sslContextFactory);
        log.debug("jettySslContextFactory: {}", sslContextFactory.dump());
        sslContextFactory.setHostnameVerifier(secureHostnameVerifier());
        if (!verifySslCertificatesOfServices) {
            sslContextFactory.setTrustAll(true);
        }

        return sslContextFactory;
    }

    /**
     * Returns RestTemplate with keystore. This RestTemplate makes calls to other systems with a certificate to sign to
     * other systems by certificate. It is necessary to call systems like DiscoverySystem etc.
     *
     * @return RestTemplate, which uses certificate from keystore to authenticate
     */
    @Bean
    @Primary
    @Qualifier("restTemplateWithKeystore")
    public RestTemplate restTemplateWithKeystore() {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(secureHttpClient);
        factory.setReadTimeout(readTimeout);
        factory.setConnectTimeout(requestConnectionTimeout);
        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.getMessageConverters().add(0,messageConverter());
        return restTemplate;
    }

    private MappingJackson2HttpMessageConverter messageConverter() {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper());
        return converter;
    }

    private ObjectMapper objectMapper(){
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
        return objectMapper;
    }

    /**
     * Returns RestTemplate without keystore. The purpose is to call z/OSMF (or other systems), which accept login by
     * certificate. In case of login into z/OSMF can certificate has higher priority. It breaks credentials
     * verification.
     *
     * @return default RestTemplate, which doesn't use certificate from keystore
     */
    @Bean
    @Qualifier("restTemplateWithoutKeystore")
    public RestTemplate restTemplateWithoutKeystore() {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(secureHttpClientWithoutKeystore);
        factory.setReadTimeout(readTimeout);
        factory.setConnectTimeout(requestConnectionTimeout);
        return new RestTemplate(factory);
    }

    /**
     * @return HttpClient which use a certificate to authenticate
     */
    @Bean
    @Primary
    @Qualifier("secureHttpClientWithKeystore")
    public CloseableHttpClient secureHttpClient() {
        return secureHttpClient;
    }

    /**
     * @return HttpClient, which doesn't use a certificate to authenticate
     */
    @Bean
    @Qualifier("secureHttpClientWithoutKeystore")
    public CloseableHttpClient secureHttpClientWithoutKeystore() {
        return secureHttpClientWithoutKeystore;
    }

    @Bean
    public SSLContext secureSslContext() {
        return secureSslContext;
    }

    @Bean
    public HostnameVerifier secureHostnameVerifier() {
        return secureHostnameVerifier;
    }

    @Bean
    public EurekaJerseyClient eurekaJerseyClient() {
        return eurekaJerseyClientBuilder.build();
    }


}
