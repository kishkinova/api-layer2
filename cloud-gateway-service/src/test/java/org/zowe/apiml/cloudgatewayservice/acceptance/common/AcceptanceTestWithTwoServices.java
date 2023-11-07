/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.cloudgatewayservice.acceptance.common;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.zowe.apiml.cloudgatewayservice.acceptance.netflix.ApplicationRegistry;
import org.zowe.apiml.cloudgatewayservice.service.InstanceInfoService;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

@Slf4j
@AcceptanceTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AcceptanceTestWithTwoServices extends AcceptanceTestWithBasePath {

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    protected ApplicationRegistry applicationRegistry;

    @MockBean
    protected InstanceInfoService instanceInfoService;

    @BeforeAll
    void mockInstanceInfoService() {
        doAnswer(invocation -> {
            String serviceId = invocation.getArgument(0);
            return Mono.just(applicationRegistry.getServiceInstance(serviceId));
        }).when(instanceInfoService).getServiceInstance(anyString());
    }

    @BeforeEach
    void resetCounters() {
        applicationRegistry.getMockServices().forEach(MockService::resetCounter);
    }

    @AfterEach
    void checkAssertionErrorsOnMockServices() {
        MockService.checkAssertionErrors();
    }

    protected void updateRoutingRules() {
        applicationEventPublisher.publishEvent(new RefreshRoutesEvent("List of services changed"));
    }

    protected MockService.MockServiceBuilder mockService(String serviceId) {
        return MockService.builder()
            .statusChangedlistener(mockService -> {
                applicationRegistry.update(mockService);
                updateRoutingRules();
            })
            .serviceId(serviceId);
    }

    @AfterEach
    void stopMocksWithTestScope() {
        applicationRegistry.afterTest();
    }

    @AfterAll
    void stopMocksWithClassScope() {
        applicationRegistry.afterClass();
    }

}
