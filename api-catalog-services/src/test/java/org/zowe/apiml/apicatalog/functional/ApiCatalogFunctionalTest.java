/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

//package org.zowe.apiml.apicatalog.functional;
//
//import io.restassured.RestAssured;
//import org.junit.jupiter.api.BeforeEach;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.boot.web.server.LocalServerPort;
//import org.springframework.test.context.ContextConfiguration;
//
//@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
//@ContextConfiguration
//public abstract class ApiCatalogFunctionalTest {
//
//    @LocalServerPort
//    protected int port;
//
//    @Value("${apiml.service.hostname:localhost}")
//    protected String hostname;
//
//    @BeforeEach
//    void setUp() {
//        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
//    }
//
//    protected String getCatalogUriWithPath(String path) {
//        return String.format("https://%s:%d", hostname, port) + path;
//    }
//}
