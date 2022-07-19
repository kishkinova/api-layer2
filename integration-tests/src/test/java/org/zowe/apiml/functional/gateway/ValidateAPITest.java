/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.functional.gateway;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.zowe.apiml.util.TestWithStartedInstances;
import org.zowe.apiml.util.categories.GatewayTest;
import org.zowe.apiml.util.categories.TestsNotMeantForZowe;
import org.zowe.apiml.util.config.ConfigReader;
import org.zowe.apiml.util.config.GatewayServiceConfiguration;


import io.restassured.RestAssured;


import static io.restassured.RestAssured.given;

import java.net.URISyntaxException;

/**
 * This is an integration test class for ValidateAPIController.java
 * It tests ValidateAPIController with service verificationOnboardService.java
 * 
 * The test senario is:
 * Send serviceId to validateAPIController then the controller will call verificationOnboardService.java
 * to check if the service is registered and metadata can be retrieved then it will return appropriate message
 * The controller recieve the response from the service and then will post response.
 * 
 */
@GatewayTest
public class ValidateAPITest implements TestWithStartedInstances {

    private String gatewayScheme;
    private String gatewayHost;
    private int gatewayPort;

    private String gatewayUrl;


    public ValidateAPITest() throws URISyntaxException {
        initGatewayService();
    }

    @Test
    @TestsNotMeantForZowe
    void givenValidServiceId() {
        given()  
            .log().all()
            .param("serviceID","discoverableclient")        
        .when()          
            .post(gatewayUrl)
        .then()
            .assertThat()
            .statusCode(HttpStatus.SC_OK);

    }


    private void initGatewayService() {
        GatewayServiceConfiguration gatewayServiceConfiguration = ConfigReader.environmentConfiguration().getGatewayServiceConfiguration();
        gatewayScheme = gatewayServiceConfiguration.getScheme();
        gatewayHost = gatewayServiceConfiguration.getHost();
        gatewayPort = gatewayServiceConfiguration.getExternalPort();
        RestAssured.port = gatewayPort;
        RestAssured.useRelaxedHTTPSValidation();

        gatewayUrl = String.format("%s://%s:%d%s", gatewayScheme, gatewayHost, gatewayPort, "/validate");
    }
}