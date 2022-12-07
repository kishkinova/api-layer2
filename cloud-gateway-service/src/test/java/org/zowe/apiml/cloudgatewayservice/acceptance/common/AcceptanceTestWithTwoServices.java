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

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.zowe.apiml.cloudgatewayservice.acceptance.netflix.ApimlDiscoveryClientStub;
import org.zowe.apiml.cloudgatewayservice.acceptance.netflix.ApplicationRegistry;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@AcceptanceTest
public class AcceptanceTestWithTwoServices extends AcceptanceTestWithBasePath {

    @Autowired
    @Qualifier("test")
    protected ApimlDiscoveryClientStub discoveryClient;
    @Autowired
    protected ApplicationRegistry applicationRegistry;

    protected HttpServer server;
    protected Service serviceWithDefaultConfiguration = new Service("serviceid2", "/serviceid2/**", "serviceid2");
    protected Service serviceWithCustomConfiguration = new Service("serviceid1", "/serviceid1/**", "serviceid1");

    @BeforeEach
    public void prepareApplications() {
        applicationRegistry.clearApplications();
    }

    @AfterEach
    public void tearDown() {
        server.stop(0);
    }

    protected AtomicInteger mockServerWithSpecificHttpResponse(int statusCode, String serviceId, int port, Consumer<Headers> assertion) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        AtomicInteger counter = new AtomicInteger();
        server.createContext("/" + serviceId + "/test", (t) -> {
            t.sendResponseHeaders(statusCode, 0);
            assertion.accept(t.getRequestHeaders());
            t.getResponseBody().close();
            counter.getAndIncrement();
        });
        server.setExecutor(null);
        server.start();
        return counter;
    }
}
