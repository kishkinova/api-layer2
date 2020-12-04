/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.gatewayservice.authentication;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.zowe.apiml.util.categories.zOSMFAuthTest;

import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.zowe.apiml.gatewayservice.SecurityUtils.getConfiguredSslConfig;

@zOSMFAuthTest
@SuppressWarnings({"squid:S2187"})
class ZosmfLogoutTest extends LogoutTest {

    // Change to dummy and run the same test as for the zOSMF
    @BeforeAll
    static void switchToTestedProvider() {
        RestAssured.useRelaxedHTTPSValidation();
        RestAssured.config = RestAssured.config().sslConfig(getConfiguredSslConfig());

    }

    @Test
    void givenValidToken_whenLogoutCalledTwice_thenSecondCallUnauthorized() {
        String jwt = generateToken();

        assertIfLogged(jwt, true);

        assertLogout(jwt, SC_NO_CONTENT);
        assertLogout(jwt, SC_UNAUTHORIZED);
    }
}
