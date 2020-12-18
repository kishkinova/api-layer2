/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.security.common.login;

import org.zowe.apiml.security.common.config.AuthConfigurationProperties;
import org.zowe.apiml.security.common.token.TokenAuthentication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SuccessfulLoginHandlerTest {
    private AuthConfigurationProperties authConfigurationProperties;
    private MockHttpServletRequest httpServletRequest;
    private MockHttpServletResponse httpServletResponse;
    private SuccessfulLoginHandler successfulLoginHandler;

    @BeforeEach
    void setup() {
        httpServletRequest = new MockHttpServletRequest();
        httpServletResponse = new MockHttpServletResponse();

        authConfigurationProperties = new AuthConfigurationProperties();
        successfulLoginHandler = new SuccessfulLoginHandler(authConfigurationProperties);
    }

    @Test
    void testOnAuthenticationSuccess() {
        successfulLoginHandler.onAuthenticationSuccess(
            httpServletRequest,
            httpServletResponse,
            new TokenAuthentication("TEST_TOKEN_STRING")
        );

        assertEquals(HttpStatus.NO_CONTENT.value(), httpServletResponse.getStatus());
        assertNotNull(httpServletResponse.getCookie(authConfigurationProperties.getCookieProperties().getCookieName()));
    }
}
