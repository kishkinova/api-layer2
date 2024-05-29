/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.zaas.security.config;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import javax.net.ssl.SSLException;

import static io.restassured.RestAssured.given;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;
import static org.zowe.apiml.security.SecurityUtils.COOKIE_AUTH_NAME;

/**
 * Simple Spring Context test to verify attls filter chain setup is in place with the right properties being sent
 */
@SpringBootTest
@TestPropertySource(
    properties = {
        "server.internal.ssl.enabled=false",
        "server.attls.enabled=true",
        "server.ssl.enabled=false",
        "server.service.scheme=http"
    }
)
@ActiveProfiles({"acceptance", "AttlsConfigTest"})
@TestInstance(Lifecycle.PER_CLASS)
public class AttlsConfigTest {

    @Autowired
    HttpSecurity http;

    @LocalServerPort
    protected int port;

    @Value("${apiml.service.hostname:localhost}")
    protected String hostname;

    @Nested
    class GivenAttlsModeEnabled {

        @Nested
        class WhenContextLoads {
            @Test
            void requestFailsWithHttps() {
                try {
                    given()
                        .log().all()
                        .cookie(COOKIE_AUTH_NAME, "jwttoken")
                    .when()
                        .get(String.format("http://%s:%d", hostname, port))
                    .then()
                        .log().all()
                        .statusCode(is(HttpStatus.SC_INTERNAL_SERVER_ERROR));
                    fail("Expected SSL failure");
                } catch (Exception e) {
                    assertInstanceOf(SSLException.class, e);
                }
            }

            @Test
            void requestFailsWithAttlsContextReasonWithHttp() {
                given()
                    .log().all()
                    .cookie(COOKIE_AUTH_NAME, "jwttoken")
                .when()
                    .get(String.format("http://%s:%d", hostname, port))
                .then()
                    .log().all()
                    .statusCode(is(HttpStatus.SC_INTERNAL_SERVER_ERROR))
                    .body(containsString("Connection is not secure."))
                    .body(containsString("AttlsContext.getStatConn"));
            }

        }

    }

}
