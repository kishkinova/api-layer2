/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.apicatalog;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;    
import org.springframework.http.HttpHeaders;
import org.zowe.apiml.util.config.ConfigReader;
import org.zowe.apiml.util.service.DiscoveryUtils;

import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.core.Is.is;
import static org.zowe.apiml.gatewayservice.SecurityUtils.getConfiguredSslConfig;

class ApiCatalogSecurityIntegrationTest {

    private final static String PASSWORD = ConfigReader.environmentConfiguration().getCredentials().getPassword();
    private final static String USERNAME = ConfigReader.environmentConfiguration().getCredentials().getUser();
    private final static String GATEWAY_SCHEME = ConfigReader.environmentConfiguration().getGatewayServiceConfiguration().getScheme();
    private final static String GATEWAY_HOST = ConfigReader.environmentConfiguration().getGatewayServiceConfiguration().getHost();
    private final static int GATEWAY_PORT = ConfigReader.environmentConfiguration().getGatewayServiceConfiguration().getPort();

    private static final String CATALOG_PREFIX = "/api/v1";
    private static final String CATALOG_SERVICE_ID = "/apicatalog";
    private static final String CATALOG_APIDOC_ENDPOINT = "/apidoc/discoverableclient/v1";
    private static final String CATALOG_ACTUATOR_ENDPOINT = "/application";

    private final static String COOKIE = "apimlAuthenticationToken";
    private final static String BASIC_AUTHENTICATION_PREFIX = "Basic";
    private final static String INVALID_USERNAME = "incorrectUser";
    private final static String INVALID_PASSWORD = "incorrectPassword";

    private static Stream<Arguments> data() {
        return Stream.of(
            Arguments.of(CATALOG_APIDOC_ENDPOINT),
            Arguments.of(CATALOG_ACTUATOR_ENDPOINT)
        );
    }

    @BeforeEach
    void setUp() {
        RestAssured.useRelaxedHTTPSValidation();
    }

    //@formatter:off
    @ParameterizedTest
    @MethodSource("data")
    void accessProtectedEndpointWithoutCredentials(String endpoint) {
        String expectedMessage = "Authentication is required for URL '" + CATALOG_SERVICE_ID + endpoint + "'";

        given()
        .when()
            .get(String.format("%s://%s:%d%s%s%s", GATEWAY_SCHEME, GATEWAY_HOST, GATEWAY_PORT, CATALOG_PREFIX,
                CATALOG_SERVICE_ID, endpoint))
        .then()
            .statusCode(is(SC_UNAUTHORIZED))
            .header(HttpHeaders.WWW_AUTHENTICATE, BASIC_AUTHENTICATION_PREFIX)
            .body("messages.find { it.messageNumber == 'ZWEAS105E' }.messageContent", equalTo(expectedMessage)
            );
    }

    @ParameterizedTest
    @MethodSource("data")
    void accessProtectedEndpointWithBasicAuth(String endpoint) {
        given()
            .auth().preemptive().basic(USERNAME, PASSWORD)
        .when()
            .get(String.format("%s://%s:%d%s%s%s", GATEWAY_SCHEME, GATEWAY_HOST, GATEWAY_PORT, CATALOG_PREFIX,
                CATALOG_SERVICE_ID, endpoint))
        .then()
            .statusCode(is(SC_OK));
    }

    @ParameterizedTest
    @MethodSource("data")
    void accessProtectedEndpointWithInvalidBasicAuth(String endpoint) {
        String expectedMessage = "Invalid username or password for URL '" + CATALOG_SERVICE_ID + endpoint + "'";

        given()
            .auth().preemptive().basic(INVALID_USERNAME, INVALID_PASSWORD)
        .when()
            .get(String.format("%s://%s:%d%s%s%s", GATEWAY_SCHEME, GATEWAY_HOST, GATEWAY_PORT, CATALOG_PREFIX,
                CATALOG_SERVICE_ID, endpoint))
        .then()
            .statusCode(is(SC_UNAUTHORIZED))
            .body("messages.find { it.messageNumber == 'ZWEAS120E' }.messageContent", equalTo(expectedMessage));
    }

    @ParameterizedTest
    @MethodSource("data")
    void accessProtectedEndpointWithInvalidCookieCatalog(String endpoint) {
        String expectedMessage = "Token is not valid for URL '" + CATALOG_SERVICE_ID + endpoint + "'";
        String invalidToken = "nonsense";

        RestAssured.config = RestAssured.config().sslConfig(getConfiguredSslConfig());
        String catalogUrl = DiscoveryUtils.getInstances("APICATALOG").get(0).getUrl();

        given()
            .cookie(COOKIE, invalidToken)
        .when()
            .get(String.format("%s%s%s", catalogUrl, CATALOG_SERVICE_ID, endpoint))
        .then()
            .statusCode(is(SC_UNAUTHORIZED))
            .body("messages.find { it.messageNumber == 'ZWEAS130E' }.messageContent", equalTo(expectedMessage));
    }

    @ParameterizedTest
    @MethodSource("data")
    void accessProtectedEndpointWithInvalidCookieGateway(String endpoint) {
        String expectedMessage = "Token is not valid for URL '" + CATALOG_SERVICE_ID + endpoint + "'";
        String invalidToken = "nonsense";

        given()
             .cookie(COOKIE, invalidToken)
        .when()
            .get(String.format("%s://%s:%d%s%s%s", GATEWAY_SCHEME, GATEWAY_HOST, GATEWAY_PORT, CATALOG_PREFIX,
                CATALOG_SERVICE_ID, endpoint))
        .then()
            .statusCode(is(SC_UNAUTHORIZED))
            .body("messages.find { it.messageNumber == 'ZWEAS130E' }.messageContent", equalTo(expectedMessage));
    }

    //@formatter:on
}
