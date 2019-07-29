/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package com.ca.apiml.security.login;

import com.ca.apiml.security.config.SecurityConfigurationProperties;
import com.ca.apiml.security.token.TokenAuthentication;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
 * Handles the successful login
 */
@Component
@RequiredArgsConstructor
public class SuccessfulLoginHandler implements AuthenticationSuccessHandler {

    private final SecurityConfigurationProperties securityConfigurationProperties;

    /**
     * Set cookie and http response on successful authentication
     *
     * @param request        the http request
     * @param response       the http response
     * @param authentication the successful authentication
     */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        TokenAuthentication tokenAuthentication = (TokenAuthentication) authentication;
        String token = tokenAuthentication.getCredentials();

        setCookie(token, response);
        response.setStatus(HttpStatus.NO_CONTENT.value());
    }

    /**
     * Add the cookie to the response
     *
     * @param token    the authentication token
     * @param response send back this response
     */
    private void setCookie(String token, HttpServletResponse response) {
        Cookie tokenCookie = new Cookie(securityConfigurationProperties.getCookieProperties().getCookieName(), token);
        tokenCookie.setComment(securityConfigurationProperties.getCookieProperties().getCookieComment());
        tokenCookie.setPath(securityConfigurationProperties.getCookieProperties().getCookiePath());
        tokenCookie.setHttpOnly(true);
        tokenCookie.setMaxAge(securityConfigurationProperties.getCookieProperties().getCookieMaxAge());
        tokenCookie.setSecure(securityConfigurationProperties.getCookieProperties().isCookieSecure());

        response.addCookie(tokenCookie);
    }
}
