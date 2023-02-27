/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.cloudgatewayservice.config;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.HealthCheckHandler;
import com.netflix.discovery.AbstractDiscoveryClientOptionalArgs;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.shared.transport.jersey.EurekaJerseyClient;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.gateway.config.GlobalCorsProperties;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.cloud.gateway.discovery.DiscoveryLocatorProperties;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping;
import org.springframework.cloud.netflix.eureka.CloudEurekaClient;
import org.springframework.cloud.netflix.eureka.MutableDiscoveryClientOptionalArgs;
import org.springframework.cloud.util.ProxyUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.pattern.PathPatternParser;
import org.zowe.apiml.cloudgatewayservice.service.ProxyRouteLocator;
import org.zowe.apiml.cloudgatewayservice.service.RouteLocator;
import org.zowe.apiml.message.core.MessageService;
import org.zowe.apiml.message.yaml.YamlMessageServiceInstance;
import org.zowe.apiml.security.HttpsConfig;
import org.zowe.apiml.security.HttpsFactory;
import org.zowe.apiml.security.SecurityUtils;
import org.zowe.apiml.util.CorsUtils;
import reactor.netty.http.client.HttpClient;

import javax.annotation.PostConstruct;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Configuration
@Slf4j
public class HttpConfig {

    private static final char[] KEYRING_PASSWORD = "password".toCharArray();

    @Value("${server.ssl.protocol:TLSv1.2}")
    private String protocol;

    @Value("${server.ssl.trustStore:#{null}}")
    private String trustStorePath;

    @Value("${server.ssl.trustStorePassword:#{null}}")
    private char[] trustStorePassword;

    @Value("${server.ssl.trustStoreType:PKCS12}")
    private String trustStoreType;

    @Value("${server.ssl.keyAlias:#{null}}")
    private String keyAlias;

    @Value("${server.ssl.keyStore:#{null}}")
    private String keyStorePath;

    @Value("${server.ssl.keyStorePassword:#{null}}")
    private char[] keyStorePassword;

    @Value("${server.ssl.keyPassword:#{null}}")
    private char[] keyPassword;

    @Value("${server.ssl.keyStoreType:PKCS12}")
    private String keyStoreType;

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

    @Value("${apiml.gateway.timeout:60}")
    private int requestTimeout;
    @Value("${apiml.service.corsEnabled:false}")
    private boolean corsEnabled;
    @Value("${apiml.service.ignoredHeadersWhenCorsEnabled:-}")
    private String ignoredHeadersWhenCorsEnabled;
    private final ApplicationContext context;

    public HttpConfig(ApplicationContext context) {
        this.context = context;
    }

    @PostConstruct
    public void init() {
        if (SecurityUtils.isKeyring(keyStorePath)) {
            keyStorePath = SecurityUtils.formatKeyringUrl(keyStorePath);
            if (keyStorePassword == null) keyStorePassword = KEYRING_PASSWORD;
        }
        if (SecurityUtils.isKeyring(trustStorePath)) {
            trustStorePath = SecurityUtils.formatKeyringUrl(trustStorePath);
            if (trustStorePassword == null) trustStorePassword = KEYRING_PASSWORD;
        }
    }

    @Bean
    @Qualifier("apimlEurekaJerseyClient")
    EurekaJerseyClient getEurekaJerseyClient() {
        return factory().createEurekaJerseyClientBuilder(eurekaServerUrl, serviceId).build();
    }


    HttpsFactory factory() {
        HttpsConfig config = HttpsConfig.builder()
            .protocol(protocol)
            .verifySslCertificatesOfServices(verifySslCertificatesOfServices)
            .nonStrictVerifySslCertificatesOfServices(nonStrictVerifySslCertificatesOfServices)
            .trustStorePassword(trustStorePassword).trustStoreRequired(trustStoreRequired)
            .trustStore(trustStorePath).trustStoreType(trustStoreType)
            .keyAlias(keyAlias).keyStore(keyStorePath).keyPassword(keyPassword)
            .keyStorePassword(keyStorePassword).keyStoreType(keyStoreType).build();
        log.info("Using HTTPS configuration: {}", config.toString());

        return new HttpsFactory(config);
    }

    @Bean
    HttpClientCustomizer secureCustomizer() {
        return httpClient -> httpClient.secure(b -> b.sslContext(sslContext()));
    }

    SslContext sslContext() {
        try {
            KeyStore keyStore = KeyStore.getInstance(this.keyStoreType);
            try (InputStream inStream = Files.newInputStream(Paths.get(keyStorePath))) {
                keyStore.load(inStream, keyStorePassword);
            }
            KeyStore trustStore = KeyStore.getInstance(this.trustStoreType);
            try (InputStream inStream = Files.newInputStream(Paths.get(this.trustStorePath))) {
                trustStore.load(inStream, this.trustStorePassword);
            }

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, keyStorePassword);
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

            trustManagerFactory.init(trustStore);
            return SslContextBuilder.forClient().keyManager(keyManagerFactory).trustManager(trustManagerFactory).build();
        } catch (Exception e) {
            log.error("Exception while creating SSL context", e);
            System.exit(1);
            return null;
        }
    }

    @Bean(destroyMethod = "shutdown")
    @RefreshScope
    @ConditionalOnMissingBean(EurekaClient.class)
    public EurekaClient eurekaClient(ApplicationInfoManager manager, EurekaClientConfig config,
                                     @Qualifier("apimlEurekaJerseyClient") EurekaJerseyClient eurekaJerseyClient,
                                     @Autowired(required = false) HealthCheckHandler healthCheckHandler) {
        ApplicationInfoManager appManager;
        if (AopUtils.isAopProxy(manager)) {
            appManager = ProxyUtils.getTargetObject(manager);
        } else {
            appManager = manager;
        }
        AbstractDiscoveryClientOptionalArgs<?> args = new MutableDiscoveryClientOptionalArgs();
        args.setEurekaJerseyClient(eurekaJerseyClient);

        CloudEurekaClient cloudEurekaClient = new CloudEurekaClient(appManager, config, args,
            this.context);
        cloudEurekaClient.registerHealthCheck(healthCheckHandler);
        return cloudEurekaClient;
    }


    @Bean
    @ConditionalOnProperty(name = "apiml.service.gateway.proxy.enabled", havingValue = "false")
    public RouteLocator apimlDiscoveryRouteDefLocator(
        ReactiveDiscoveryClient discoveryClient, DiscoveryLocatorProperties properties, List<FilterDefinition> filters, ApplicationContext context, CorsUtils corsUtils) {
        return new RouteLocator(discoveryClient, properties, filters, context, corsUtils);
    }

    @Bean
    @ConditionalOnProperty(name = "apiml.service.gateway.proxy.enabled", havingValue = "true")
    public RouteLocator proxyRouteDefLocator(
        ReactiveDiscoveryClient discoveryClient, DiscoveryLocatorProperties properties, List<FilterDefinition> filters, ApplicationContext context, CorsUtils corsUtils) {
        return new ProxyRouteLocator(discoveryClient, properties, filters, context, corsUtils);
    }

    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultCustomizer() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
            .circuitBreakerConfig(CircuitBreakerConfig.ofDefaults()).timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(requestTimeout)).build()).build());
    }

    @Bean
    public WebClient webClient() {
        HttpClient client = HttpClient.create().secure(ssl -> ssl.sslContext(sslContext()));
        return WebClient.builder().clientConnector(new ReactorClientHttpConnector(client)).build();

    }

    @Bean
    public List<FilterDefinition> filters() {
        FilterDefinition circuitBreakerFilter = new FilterDefinition();
        circuitBreakerFilter.setName("CircuitBreaker");
        FilterDefinition retryFilter = new FilterDefinition();
        retryFilter.setName("Retry");

        retryFilter.addArg("retries", "5");
        retryFilter.addArg("statuses", "SERVICE_UNAVAILABLE");
        List<FilterDefinition> filters = new ArrayList<>();
        filters.add(circuitBreakerFilter);
        filters.add(retryFilter);
        for (String headerName : ignoredHeadersWhenCorsEnabled.split(",")) {
            FilterDefinition removeHeaders = new FilterDefinition();
            removeHeaders.setName("RemoveRequestHeader");
            Map<String, String> args = new HashMap<>();
            args.put("name", headerName);
            removeHeaders.setArgs(args);
            filters.add(removeHeaders);
        }
        return filters;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(RoutePredicateHandlerMapping handlerMapping, GlobalCorsProperties globalCorsProperties, CorsUtils corsUtils) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource(new PathPatternParser());
        source.setCorsConfigurations(globalCorsProperties.getCorsConfigurations());
        corsUtils.registerDefaultCorsConfiguration(source::registerCorsConfiguration);
        handlerMapping.setCorsConfigurationSource(source);
        return source;
    }

    @Bean
    public CorsUtils corsUtils() {
        return new CorsUtils(corsEnabled);
    }

    @Bean
    public MessageService messageService() {
        MessageService messageService = YamlMessageServiceInstance.getInstance();
        messageService.loadMessages("/cloud-gateway-log-messages.yml");
        return messageService;
    }

}
