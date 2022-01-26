/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.security.client.login;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.zowe.apiml.security.client.service.GatewaySecurityService;
import org.zowe.apiml.security.common.login.LoginRequest;
import org.zowe.apiml.security.common.token.TokenAuthentication;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatewayLoginProviderTest {

    private static final String USER = "USER";
    private static final String VALID_PASSWORD = "PASS";
    private static final String INVALID_PASSWORD = "WORD";
    private static final String VALID_TOKEN = "VALID_TOKEN";

    private final GatewaySecurityService gatewaySecurityService = mock(GatewaySecurityService.class);
    private final GatewayLoginProvider gatewayLoginProvider = new GatewayLoginProvider(gatewaySecurityService);

    @Nested
    class WhenAuthenticating {
        @Nested
        class FailAuthentication {
            @Test
            void givenInvalidUsernamePassword() {
                when(gatewaySecurityService.login(USER, INVALID_PASSWORD, null)).thenReturn(Optional.empty());

                Authentication auth = new UsernamePasswordAuthenticationToken(USER, new LoginRequest(USER, INVALID_PASSWORD));
                assertThrows(BadCredentialsException.class, () -> gatewayLoginProvider.authenticate(auth));
            }
        }

        @Nested
        class AuthenticationSuccess {
            @Test
            void givenValidLoginRequestWithUsernamePassword() {
                when(gatewaySecurityService.login(USER, VALID_PASSWORD, null)).thenReturn(Optional.of(VALID_TOKEN));

                Authentication auth = new UsernamePasswordAuthenticationToken(USER, new LoginRequest(USER, VALID_PASSWORD));
                Authentication processedAuthentication = gatewayLoginProvider.authenticate(auth);

                assertTrue(processedAuthentication instanceof TokenAuthentication);
                assertTrue(processedAuthentication.isAuthenticated());
                assertEquals(VALID_TOKEN, processedAuthentication.getCredentials());
                assertEquals(USER, processedAuthentication.getName());
            }

            @Test
            void givenValidUsernamePassword() {
                when(gatewaySecurityService.login(USER, VALID_PASSWORD,null)).thenReturn(Optional.of(VALID_TOKEN));

                Authentication auth = new UsernamePasswordAuthenticationToken(USER, VALID_PASSWORD);
                Authentication processedAuthentication = gatewayLoginProvider.authenticate(auth);

                assertTrue(processedAuthentication instanceof TokenAuthentication);
                assertTrue(processedAuthentication.isAuthenticated());
                assertEquals(VALID_TOKEN, processedAuthentication.getCredentials());
                assertEquals(USER, processedAuthentication.getName());
            }
        }
    }

    @Nested
    class WhenVerifyingSupport {
        @Nested
        class Support {
            @Test
            void givenUsernamePasswordAuthenticationToken() {
                assertTrue(gatewayLoginProvider.supports(UsernamePasswordAuthenticationToken.class));
            }
        }

        @Nested
        class NotSupport {
            @Test
            void shouldNotSupportGenericAuthentication() {
                assertFalse(gatewayLoginProvider.supports(Authentication.class));
                assertFalse(gatewayLoginProvider.supports(TokenAuthentication.class));
            }
        }


    }


}
