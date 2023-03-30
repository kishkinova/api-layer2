/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.gateway.security.login.dummy;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.zowe.apiml.gateway.security.service.AuthenticationService;
import org.zowe.apiml.security.common.login.LoginRequest;
import org.zowe.apiml.security.common.token.TokenAuthentication;

import static org.zowe.apiml.security.SecurityUtils.readPassword;

/**
 * Authentication provider for development purposes
 * <p>
 * Allows Gateway to run without mainframe (z/OSMF service)
 */
@Component
public class DummyAuthenticationProvider extends DaoAuthenticationProvider {
    private static final String DUMMY_PROVIDER = "Dummy provider";

    private final AuthenticationService authenticationService;

    public DummyAuthenticationProvider(BCryptPasswordEncoder encoder,
                                       @Qualifier("dummyService") UserDetailsService userDetailsService,
                                       AuthenticationService authenticationService) {
        super();
        this.setPasswordEncoder(encoder);
        this.setUserDetailsService(userDetailsService);
        this.authenticationService = authenticationService;
    }

    /**
     * Authenticate dummy credentials
     *
     * @param authentication that was presented to the provider for validation
     * @return the authenticated token
     */
    @Override
    public Authentication authenticate(Authentication authentication) {
        UsernamePasswordAuthenticationToken usernamePasswordAuthentication;

        try {
            char[] password;
            if (authentication.getCredentials() instanceof LoginRequest) {
                password = ((LoginRequest) authentication.getCredentials()).getPassword();
            } else {
                password = readPassword(authentication.getCredentials());
            }

            authentication = new UsernamePasswordAuthenticationToken(authentication.getPrincipal(), password);
            usernamePasswordAuthentication
                = (UsernamePasswordAuthenticationToken) super.authenticate(authentication);
        } catch (AuthenticationException exception) {
            throw new BadCredentialsException("Invalid Credentials");
        } catch (Exception e) {
            throw new AuthenticationServiceException("A failure occurred when authenticating.", e);
        }

        String username = usernamePasswordAuthentication.getName();
        String token = authenticationService.createJwtToken(username, DUMMY_PROVIDER, null);

        TokenAuthentication tokenAuthentication = new TokenAuthentication(username, token);
        tokenAuthentication.setAuthenticated(true);
        return tokenAuthentication;
    }

}
