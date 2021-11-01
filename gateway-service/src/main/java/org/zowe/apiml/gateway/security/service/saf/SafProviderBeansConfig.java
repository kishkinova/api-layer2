/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.gateway.security.service.saf;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.zowe.apiml.gateway.security.service.AuthenticationService;
import org.zowe.apiml.passticket.PassTicketService;

@Configuration
@RequiredArgsConstructor
public class SafProviderBeansConfig {
    @Bean
    @ConditionalOnProperty(name = "apiml.security.saf.provider", havingValue = "rest")
    public SafIdtProvider restSafProvider(
        RestTemplate restTemplate,
        AuthenticationService authenticationService,
        PassTicketService passTicketService
    ) {
        return new SafRestAuthenticationService(restTemplate, authenticationService, passTicketService);
    }
}
