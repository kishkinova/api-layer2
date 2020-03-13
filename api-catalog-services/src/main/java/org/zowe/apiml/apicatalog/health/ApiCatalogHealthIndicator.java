/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.apicatalog.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.zowe.apiml.product.constants.CoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;

/**
 * Api Catalog health information (/application/health)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApiCatalogHealthIndicator extends AbstractHealthIndicator {
    private static String AUTHENTICATION_SERVICE_ID = "apiml.authorizationService.zosmfServiceId";
    private static String AUTHENTICATION_SERVICE_PROVIDER = "apiml.authorizationService.provider";
    private static String ZOSMF = "zosmf";

    private final DiscoveryClient discoveryClient;

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        String gatewayServiceId = CoreService.GATEWAY.getServiceId();
        String authServiceId = ZOSMF;
        Status healthStatus = Status.DOWN;

        boolean gatewayUp = !this.discoveryClient.getInstances(gatewayServiceId).isEmpty();

        if (gatewayUp) {
            healthStatus = authorizationServiceUp() ? Status.UP : Status.DOWN;
        }

        builder
            .status(healthStatus)
            .withDetail(gatewayServiceId, healthStatus.getCode())
            .withDetail(authServiceId, healthStatus.getCode());
    }

    private boolean authorizationServiceUp() {
        String authServiceProvider;
        String authServiceId;
        String gatewayServiceId = CoreService.GATEWAY.getServiceId();
        boolean authServiceUp = false;

        authServiceId = this.discoveryClient.getInstances(gatewayServiceId).get(0).getMetadata().get(AUTHENTICATION_SERVICE_ID);
        authServiceProvider = this.discoveryClient.getInstances(gatewayServiceId).get(0).getMetadata().get(AUTHENTICATION_SERVICE_PROVIDER);

        if (authServiceProvider.equalsIgnoreCase(ZOSMF)) {
            authServiceUp = !this.discoveryClient.getInstances(authServiceId).isEmpty();
        } else if (authServiceProvider.equalsIgnoreCase("dummy")) {
            authServiceUp = true;
        }

        return authServiceUp;
    }
}
