/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.zaas.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.commons.util.IdUtils;
import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.cloud.netflix.eureka.EurekaInstanceConfigBean;
import org.springframework.cloud.netflix.eureka.metadata.ManagementMetadata;
import org.springframework.cloud.netflix.eureka.metadata.ManagementMetadataProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertyResolver;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.util.StringUtils;
import org.zowe.apiml.product.gateway.GatewayClient;
import org.zowe.apiml.product.instance.ServiceAddress;
import org.zowe.apiml.product.routing.transform.TransformService;

import java.util.Map;

import static org.zowe.apiml.constants.EurekaMetadataDefinition.APIML_ID;
import static org.zowe.apiml.constants.EurekaMetadataDefinition.SERVICE_EXTERNAL_URL;

@Configuration
@RequiredArgsConstructor
@EnableRetry
@Slf4j
public class ZaasConfig {

    private static final String SEPARATOR = ":";

    private final ConfigurableEnvironment env;

    @Bean
    public ServiceAddress getZaasAddress(@Value("${apiml.zaas.hostname}") String hostname,
                                         @Value("${apiml.service.port}") String port, @Value("${apiml.service.scheme}") String scheme) {
        return ServiceAddress.builder().scheme(scheme).hostname(hostname + ":" + port).build();
    }

    @Bean
    public TransformService transformService(GatewayClient gatewayClient) {
        return new TransformService(gatewayClient);
    }

    @Bean
    public EurekaInstanceConfigBean eurekaInstanceConfigBean(InetUtils inetUtils,
                                                             ManagementMetadataProvider managementMetadataProvider) {
        String hostname = getProperty("eureka.instance.hostname");
        boolean preferIpAddress = Boolean
            .parseBoolean(getProperty("eureka.instance.prefer-ip-address"));
        String ipAddress = getProperty("eureka.instance.ip-address");

        String serverContextPath = env.getProperty("server.servlet.context-path", "/");

        Integer managementPort = env.getProperty("management.server.port", Integer.class);

        String managementContextPath = env
            .getProperty("management.server.servlet.context-path");

        EurekaInstanceConfigBean instance = new EurekaInstanceConfigBean(inetUtils);

        int serverPort = Integer
            .parseInt(getEnabledPort(env));

        boolean isSecurePortEnabled = Boolean.parseBoolean(getProperty("server.ssl.enabled"));
        instance.setNonSecurePort(isSecurePortEnabled ? 0 : serverPort);
        instance.setNonSecurePortEnabled(!isSecurePortEnabled);
        instance.setSecurePort(isSecurePortEnabled ? serverPort : 0);
        instance.setSecurePortEnabled(isSecurePortEnabled);

        instance.setInstanceId(getInstanceId(env));

        instance.setPreferIpAddress(preferIpAddress);
        if (StringUtils.hasText(ipAddress)) {
            instance.setIpAddress(ipAddress);
        }

        if (StringUtils.hasText(hostname)) {
            instance.setHostname(hostname);
        }

        String externalUrl = getProperty("apiml.service.external-url");
        if (!StringUtils.hasText(externalUrl)) {
            externalUrl = (isSecurePortEnabled ? "https" : "http") + "://" + hostname + ":" + serverPort;
        }
        instance.getMetadataMap().put(SERVICE_EXTERNAL_URL, externalUrl);

        String apimlId = getProperty("apiml.service.apiml-id");
        if (!StringUtils.hasText(apimlId)) {
            apimlId = hostname + "_" + serverPort;
        }
        instance.getMetadataMap().put(APIML_ID, apimlId);

        String statusPageUrlPath = getProperty("eureka.instance.status-page-url-path");
        String healthCheckUrlPath = getProperty("eureka.instance.health-check-url-path");

        if (StringUtils.hasText(statusPageUrlPath)) {
            instance.setStatusPageUrlPath(statusPageUrlPath);
        }
        if (StringUtils.hasText(healthCheckUrlPath)) {
            instance.setHealthCheckUrlPath(healthCheckUrlPath);
        }

        ManagementMetadata metadata = managementMetadataProvider.get(instance, serverPort,
            serverContextPath, managementContextPath, managementPort);

        if (metadata != null) {
            instance.setStatusPageUrl(metadata.getStatusPageUrl());
            instance.setHealthCheckUrl(metadata.getHealthCheckUrl());
            if (instance.isSecurePortEnabled()) {
                instance.setSecureHealthCheckUrl(metadata.getSecureHealthCheckUrl());
            }
            Map<String, String> metadataMap = instance.getMetadataMap();
            metadataMap.computeIfAbsent("management.port",
                k -> String.valueOf(metadata.getManagementPort()));
        }

        log.debug("Eureka Configuration Bean (Environment variables or externalized configuration overrides this configuration and produce different registration in Discovery): {}", instance);

        return instance;
    }

    private String getProperty(String property) {
        return this.env.containsProperty(property) ? this.env.getProperty(property) : "";
    }

    public static String getInstanceId(PropertyResolver resolver) {
        String hostname = resolver.getProperty("apiml.service.hostname");
        String appName = resolver.getProperty("spring.application.name");

        String namePart = IdUtils.combineParts(hostname, SEPARATOR, appName);

        String indexPart = getEnabledPort(resolver);

        return IdUtils.combineParts(namePart, SEPARATOR, indexPart);
    }

    public static String getEnabledPort(PropertyResolver resolver) {
        if (Boolean.parseBoolean(resolver.getProperty("server.internal.enabled"))) {
            return resolver.getProperty("server.internal.port");
        } else {
            return resolver.getProperty("apiml.service.port");
        }
    }
}