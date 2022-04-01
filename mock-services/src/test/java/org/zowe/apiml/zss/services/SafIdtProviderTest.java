/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.zss.services;

import org.apache.commons.lang3.time.DateUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.zowe.apiml.zss.model.Authentication;
import org.zowe.apiml.zss.model.Token;

import io.jsonwebtoken.Jwts;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

public class SafIdtProviderTest {
    private SafIdtProvider underTest;

    @Nested
    class WhenAuthenticating {
        @BeforeEach
        void setUp() {
            underTest = new SafIdtProvider();
        }

        @Nested
        class GivenAuthenticationWithUsername {
            @Test
            void tokenIsReturned() {
                Authentication authentication = new Authentication();
                authentication.setUsername("validUsername");

                Optional<Token> token = underTest.authenticate(authentication);

                assertThat(token.isPresent(), is(true));
            }
        }
    }

    @Nested
    class WhenVerifying {
        private Map<String, String> tokens;

        @BeforeEach
        void setUp() {
            tokens = new HashMap<>();
            underTest = new SafIdtProvider(tokens);
        }

        @Nested
        class GivenExistingToken {
            @Test
            void tokenIsVerified() {
                String jwt = Jwts.builder()
                        .setSubject("username")
                        .setExpiration(DateUtils.addMinutes(new Date(), 10))
                        .compact();
                Token token = new Token(jwt);

                tokens.put("username", jwt);

                assertThat(underTest.verify(token), is(true));
            }
        }
    }
}
