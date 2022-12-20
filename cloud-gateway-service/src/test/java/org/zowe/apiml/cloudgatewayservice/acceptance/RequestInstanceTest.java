/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.cloudgatewayservice.acceptance;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.zowe.apiml.cloudgatewayservice.acceptance.common.AcceptanceTest;
import org.zowe.apiml.cloudgatewayservice.acceptance.common.AcceptanceTestWithTwoServices;

import java.io.IOException;

import static io.restassured.RestAssured.given;
import static org.apache.http.HttpStatus.*;
import static org.hamcrest.core.Is.is;

@AcceptanceTest
@ActiveProfiles("test")
class RequestInstanceTest extends AcceptanceTestWithTwoServices {

    @BeforeEach
    void setUp() throws IOException {
        mockServerWithSpecificHttpResponse(200, "/serviceid1/test", 4000, (headers) -> {
        }, "".getBytes());
    }

    @Nested
    class WhenValidInstanceId {

        @Test
        void routeToCorrectService() {
            given()
                .header("X-Request-Id", "serviceid1localhost")
                .when()
                .get(basePath + serviceWithCustomConfiguration.getPath())
                .then().statusCode(Matchers.is(SC_OK));
        }
    }

    @Nested
    class WhenNonExistingInstanceId {
        @Test
        void cantRouteToServer() {
            given()
                .header("X-Request-Id", "non-existing").
                when()
                .get(basePath + serviceWithCustomConfiguration.getPath())
                .then()
                .statusCode(is(SC_NOT_FOUND));
        }
    }

    @Test
    void routeToServiceWithCorsDisabled() {

        given()
            .header("Origin", "https://localhost:3000")
            .header("X-Request-Id", "serviceid1localhost")
            .when()
            .get(basePath + serviceWithCustomConfiguration.getPath())
            .then().statusCode(Matchers.is(SC_FORBIDDEN));
    }
}
