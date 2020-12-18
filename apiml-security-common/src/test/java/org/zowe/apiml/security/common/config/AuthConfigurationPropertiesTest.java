/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.security.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationServiceException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthConfigurationPropertiesTest {
    private final AuthConfigurationProperties authConfigurationProperties = new AuthConfigurationProperties();

    @Test
    void shouldThrowWhenZosmfIsNotConfigured() {
        authConfigurationProperties.setZosmfServiceId(null);
        assertThrows(AuthenticationServiceException.class, () -> authConfigurationProperties.validatedZosmfServiceId());
    }

    @Test
    void shouldReturnWhenZosmfIsConfigured() {
        authConfigurationProperties.setZosmfServiceId("ZOSMF_SID");
        assertEquals("ZOSMF_SID", authConfigurationProperties.validatedZosmfServiceId());
    }
}
