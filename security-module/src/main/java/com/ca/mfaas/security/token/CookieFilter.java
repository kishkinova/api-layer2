/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package com.ca.mfaas.security.token;

import com.ca.mfaas.security.config.SecurityConfigurationProperties;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

public class CookieFilter extends AbstractSecureContentFilter {
    private final SecurityConfigurationProperties securityConfigurationProperties;

    public CookieFilter(AuthenticationManager authenticationManager,
                        AuthenticationFailureHandler failureHandler,
                        SecurityConfigurationProperties securityConfigurationProperties) {
        super(authenticationManager, failureHandler);
        this.securityConfigurationProperties = securityConfigurationProperties;
    }

    @Override
    protected String extractContent(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(securityConfigurationProperties.getCookieProperties().getCookieName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
