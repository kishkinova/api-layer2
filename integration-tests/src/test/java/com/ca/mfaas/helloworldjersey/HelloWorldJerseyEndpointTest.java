/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package com.ca.mfaas.helloworldjersey;

import com.ca.mfaas.utils.categories.LocalDeploymentTest;
import com.ca.mfaas.utils.http.HttpRequestUtils;
import io.restassured.RestAssured;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.net.URI;

import static io.restassured.RestAssured.given;
import static org.apache.http.HttpStatus.SC_OK;
import static org.hamcrest.Matchers.is;

@Category(LocalDeploymentTest.class)
public class HelloWorldJerseyEndpointTest {
    private static final String UI_V1_PATH = "/hellojersey/";
    private static final String APP_INFO_PATH = "/app/hellojersey/info/";
    private static final String APP_INFO_HEALTH = "/app/hellojersey/health/";
    private static final String API_DOC_PATH = "/api/v1/api-doc/hellojersey/";
    private static final String GREETING_PATH = "/api/v1/hellojersey/v1/greeting";
    private static final String GREETING_WITH_NAME_PATH = "/api/v1/hellojersey/v1/greeting/Petr";
    private static final String JSON_CONTENT_TYPE = "application/json";

    @BeforeClass
    public static void beforeClass() {
        RestAssured.useRelaxedHTTPSValidation();
    }

    @Test
    public void shouldGetUI() {
        URI uri = HttpRequestUtils.getUriFromGateway(UI_V1_PATH);

        given()
        .when()
            .get(uri)
        .then()
            .statusCode(is(SC_OK))
            .contentType(is("text/html;charset=UTF-8"));
    }

    @Test
    public void shouldGetApplicationInfo() {
        URI uri = HttpRequestUtils.getUriFromGateway(APP_INFO_PATH);

        given()
        .when()
            .get(uri)
        .then()
            .statusCode(is(SC_OK))
            .contentType(is(JSON_CONTENT_TYPE));
    }


    @Test
    public void shouldGetHealth() {
        URI uri = HttpRequestUtils.getUriFromGateway(APP_INFO_HEALTH);

        given()
        .when()
            .get(uri)
        .then()
            .statusCode(is(SC_OK))
            .contentType(is(JSON_CONTENT_TYPE))
            .body("status", is("UP"));
    }

    @Test
    public void shouldGetApiDoc() {
        URI uri = HttpRequestUtils.getUriFromGateway(API_DOC_PATH);

        given()
        .when()
            .get(uri)
        .then()
            .statusCode(is(SC_OK))
            .contentType(is(JSON_CONTENT_TYPE))
            .body("info.description", is("REST API for a JavaEE Jersey Application"));
    }

    @Test
    public void shouldGetGreeting() {
        URI uri = HttpRequestUtils.getUriFromGateway(GREETING_PATH);

        given()
        .when()
            .get(uri)
        .then()
            .statusCode(is(SC_OK))
            .body("content", is("Hello, World!"));
    }

    @Test
    public void shouldGetGreetingWithName() {
        URI uri = HttpRequestUtils.getUriFromGateway(GREETING_WITH_NAME_PATH);

        given()
        .when()
            .get(uri)
        .then()
            .statusCode(is(SC_OK))
            .body("content", is("Hello, Petr!"));
    }
}
