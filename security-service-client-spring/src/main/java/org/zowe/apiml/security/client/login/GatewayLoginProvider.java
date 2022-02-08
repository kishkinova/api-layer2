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

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.zowe.apiml.security.client.service.GatewaySecurityService;
import org.zowe.apiml.security.common.login.LoginRequest;
import org.zowe.apiml.security.common.token.TokenAuthentication;

import java.util.Optional;

/**
 * Authentication provider that authenticates UsernamePasswordAuthenticationToken against Gateway
 */
@Component
@RequiredArgsConstructor
public class GatewayLoginProvider implements AuthenticationProvider {
    private final GatewaySecurityService gatewaySecurityService;

    /**
     * Authenticate the credentials
     *
     * @param authentication that was presented to the provider for validation
     * @return the authenticated token
     */
    @Override
    public Authentication authenticate(Authentication authentication) {
        String username = authentication.getPrincipal().toString();
        String password;
        String newPassword = null;
        if (authentication.getCredentials() instanceof LoginRequest) {
            LoginRequest credentials = (LoginRequest) authentication.getCredentials();
            password = credentials.getPassword();
            newPassword = LoginRequest.getNewPassword(authentication);
        } else {
            password = (String) authentication.getCredentials();
        }

        Optional<String> token = gatewaySecurityService.login(username, password, newPassword);

        if (!token.isPresent()) {
            throw new BadCredentialsException("Invalid Credentials");
        }

        TokenAuthentication tokenAuthentication = new TokenAuthentication(username, token.get());
        tokenAuthentication.setAuthenticated(true);

        return tokenAuthentication;
    }

    @Override
    public boolean supports(Class<?> auth) {
        return auth.equals(UsernamePasswordAuthenticationToken.class);
    }
}
