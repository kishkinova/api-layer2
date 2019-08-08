/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package com.ca.apiml.security.config;

import com.ca.mfaas.error.ErrorService;
import com.ca.mfaas.error.impl.ErrorServiceImpl;
import com.ca.mfaas.product.gateway.GatewayClient;
import com.ca.mfaas.product.gateway.GatewayInstanceInitializer;
import com.ca.mfaas.product.lookup.InstanceLookupExecutor;
import com.netflix.discovery.EurekaClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * General configuration of security client
 */
@Configuration
@ComponentScan("com.ca.apiml.security")
public class SecurityServiceConfiguration {

    @Bean
    public GatewayInstanceInitializer gatewayInstanceInitializer(
        ApplicationEventPublisher applicationEventPublisher,
        GatewayClient gatewayClient,
        @Qualifier("eurekaClient") EurekaClient eurekaClient) {


        return new GatewayInstanceInitializer(
            new InstanceLookupExecutor(eurekaClient),
            applicationEventPublisher, gatewayClient);
    }

    @Bean
    public ErrorService errorService() {
        return new ErrorServiceImpl("/security-service-messages.yml");
    }
}
