/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.security.client.service;

import org.zowe.apiml.security.client.handler.RestResponseHandler;
import org.zowe.apiml.security.common.config.AuthConfigurationProperties;
import org.zowe.apiml.security.common.token.QueryResponse;
import org.zowe.apiml.security.common.token.TokenNotValidException;
import org.zowe.apiml.product.gateway.GatewayClient;
import org.zowe.apiml.product.gateway.GatewayConfigProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class GatewaySecurityServiceTest {
    private static final String USERNAME = "user";
    private static final String PASSWORD = "pass";
    private static final String TOKEN = "token";
    private static final String GATEWAY_SCHEME = "https";
    private static final String GATEWAY_HOST = "localhost:10010";

    private GatewayConfigProperties gatewayConfigProperties;
    private AuthConfigurationProperties authConfigurationProperties;
    private RestTemplate restTemplate;
    private GatewaySecurityService securityService;
    private String cookie;

    @BeforeEach
    void setup() {
        gatewayConfigProperties = GatewayConfigProperties.builder()
            .scheme(GATEWAY_SCHEME)
            .hostname(GATEWAY_HOST)
            .build();
        GatewayClient gatewayClient = new GatewayClient(gatewayConfigProperties);
        authConfigurationProperties = new AuthConfigurationProperties();
        restTemplate = mock(RestTemplate.class);
        RestResponseHandler responseHandler = new RestResponseHandler();

        securityService = new GatewaySecurityService(
            gatewayClient,
            authConfigurationProperties,
            restTemplate,
            responseHandler
        );

        cookie = String.format("%s=%s",
            authConfigurationProperties.getCookieProperties().getCookieName(), TOKEN);
    }

    @Test
    void doSuccessfulLogin() {
        String uri = String.format("%s://%s%s", gatewayConfigProperties.getScheme(),
            gatewayConfigProperties.getHostname(), authConfigurationProperties.getGatewayLoginEndpoint());

        HttpEntity loginRequest = createLoginRequest();

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.add(HttpHeaders.SET_COOKIE, cookie);

        when(restTemplate.exchange(
            eq(uri),
            eq(HttpMethod.POST),
            eq(loginRequest),
            eq(String.class)))
            .thenReturn(new ResponseEntity<>(responseHeaders, HttpStatus.NO_CONTENT));

        Optional<String> token = securityService.login(USERNAME, PASSWORD);

        assertTrue(token.isPresent());
        assertEquals(TOKEN, token.get());
    }

    @Test
    void doLoginWithoutCookie() {
        String uri = String.format("%s://%s%s", gatewayConfigProperties.getScheme(),
            gatewayConfigProperties.getHostname(), authConfigurationProperties.getGatewayLoginEndpoint());

        HttpEntity loginRequest = createLoginRequest();

        when(restTemplate.exchange(
            eq(uri),
            eq(HttpMethod.POST),
            eq(loginRequest),
            eq(String.class)))
            .thenReturn(new ResponseEntity<>(HttpStatus.NO_CONTENT));

        Optional<String> token = securityService.login(USERNAME, PASSWORD);

        assertFalse(token.isPresent());
    }

    @Test
    void doLoginWhenGatewayUnauthorized() {
        String uri = String.format("%s://%s%s", gatewayConfigProperties.getScheme(),
            gatewayConfigProperties.getHostname(), authConfigurationProperties.getGatewayLoginEndpoint());

        HttpEntity loginRequest = createLoginRequest();

        when(restTemplate.exchange(
            eq(uri),
            eq(HttpMethod.POST),
            eq(loginRequest),
            eq(String.class)))
            .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        Exception exception = assertThrows(BadCredentialsException.class, () -> securityService.login(USERNAME, PASSWORD));
        assertEquals("Username or password are invalid.", exception.getMessage());
    }

    @Test
    void doSuccessfulQuery() {
        QueryResponse expectedQueryResponse = new QueryResponse("domain", "user", new Date(), new Date(), QueryResponse.Source.ZOWE);

        String uri = String.format("%s://%s%s", gatewayConfigProperties.getScheme(),
            gatewayConfigProperties.getHostname(), authConfigurationProperties.getGatewayQueryEndpoint());

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        HttpEntity httpEntity = new HttpEntity<>(headers);

        when(restTemplate.exchange(
            eq(uri),
            eq(HttpMethod.GET),
            eq(httpEntity),
            eq(QueryResponse.class)))
            .thenReturn(new ResponseEntity<>(expectedQueryResponse, HttpStatus.OK));

        QueryResponse query = securityService.query("token");

        assertEquals(expectedQueryResponse, query);
    }

    @Test
    void doQueryWhenGatewayUnauthorized() {
        String uri = String.format("%s://%s%s", gatewayConfigProperties.getScheme(),
            gatewayConfigProperties.getHostname(), authConfigurationProperties.getGatewayQueryEndpoint());

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        HttpEntity httpEntity = new HttpEntity<>(headers);

        when(restTemplate.exchange(
            eq(uri),
            eq(HttpMethod.GET),
            eq(httpEntity),
            eq(QueryResponse.class)))
            .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        Exception exception = assertThrows(TokenNotValidException.class, () -> securityService.query("token"));
        assertEquals("Token is not valid.", exception.getMessage());
    }

    private HttpEntity createLoginRequest() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode loginRequest = mapper.createObjectNode();
        loginRequest.put("username", USERNAME);
        loginRequest.put("password", PASSWORD);

        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.APPLICATION_JSON_UTF8);
        return new HttpEntity<>(loginRequest, requestHeaders);
    }
}
