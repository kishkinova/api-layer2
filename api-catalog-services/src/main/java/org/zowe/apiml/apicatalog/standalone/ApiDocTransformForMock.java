/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.apicatalog.standalone;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.zowe.apiml.product.gateway.GatewayConfigProperties;

@Configuration
@ConditionalOnProperty(value = "apiml.catalog.standalone.enabled", havingValue = "true")
public class ApiDocTransformForMock {

    @Value("${server.hostname:localhost}")
    private String hostname;

    @Value("${server.port}")
    private String port;

    @Value("${service.schema:https}")
    private String schema;

    @Bean
    @Primary
    public GatewayConfigProperties gatewayConfigPropertiesForMock() {
        return GatewayConfigProperties.builder()
            .scheme(schema)
            .hostname(String.format("%s:%s/apicatalog/mock", hostname, port))
            .build();
    }

}
