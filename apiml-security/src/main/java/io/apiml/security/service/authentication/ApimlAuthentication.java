/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package io.apiml.security.service.authentication;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.Collections;

public class ApimlAuthentication extends AbstractAuthenticationToken {
    private final String username;
    private final String token;

    public ApimlAuthentication(String username, String token) {
        super(Collections.emptyList());
        this.username = username;
        this.token = token;
    }

    @Override
    public String getCredentials() {
        return token;
    }

    @Override
    public String getPrincipal() {
        return username;
    }
}
