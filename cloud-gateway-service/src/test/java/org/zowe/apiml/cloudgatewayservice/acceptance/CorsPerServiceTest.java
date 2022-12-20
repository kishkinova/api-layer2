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
import org.junit.jupiter.api.Test;
import org.zowe.apiml.cloudgatewayservice.acceptance.common.AcceptanceTest;
import org.zowe.apiml.cloudgatewayservice.acceptance.common.AcceptanceTestWithTwoServices;

import java.io.IOException;

import static io.restassured.RestAssured.given;
import static org.apache.http.HttpStatus.SC_OK;
import static org.junit.jupiter.api.Assertions.assertTrue;

@AcceptanceTest
class CorsPerServiceTest extends AcceptanceTestWithTwoServices {

    @Test
    void routeToServiceWithCorsEnabled() throws IOException {
        mockServerWithSpecificHttpResponse(200, "/serviceid2/test", 4000, (headers) ->
            assertTrue(headers != null && headers.get("Origin") == null),
            "".getBytes()
        );
        given()
            .header("Origin", "https://localhost:3000")
            .header("X-Request-Id", "serviceid2localhost")
            .when()
            .get(basePath + serviceWithDefaultConfiguration.getPath())
            .then().statusCode(Matchers.is(SC_OK));
    }

}
