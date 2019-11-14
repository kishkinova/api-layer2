/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package com.ca.mfaas.apicatalog.config;

import com.ca.mfaas.message.core.MessageService;
import com.ca.mfaas.product.gateway.GatewayClient;
import com.ca.mfaas.product.routing.transform.TransformService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * General configuration of the API Catalog.
 */
@Configuration
public class BeanConfig {

    @Bean
    @Primary
    public MessageService messageServiceCatalog(MessageService messageService) {
        messageService.loadMessages("/security-common-log-messages.yml");
        messageService.loadMessages("/apicatalog-log-messages.yml");
        return messageService;
    }

    @Bean
    public TransformService transformService(GatewayClient gatewayClient) {
        return new TransformService(gatewayClient);
    }

}
