/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.gateway.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.zowe.apiml.gateway.controllers.AuthController;
import org.zowe.apiml.gateway.controllers.CacheServiceController;
import org.zowe.apiml.gateway.security.query.QueryFilter;
import org.zowe.apiml.gateway.security.query.SuccessfulQueryHandler;
import org.zowe.apiml.gateway.security.service.AuthenticationService;
import org.zowe.apiml.gateway.security.ticket.SuccessfulTicketHandler;
import org.zowe.apiml.security.common.config.AuthConfigurationProperties;
import org.zowe.apiml.security.common.config.HandlerInitializer;
import org.zowe.apiml.security.common.content.BasicContentFilter;
import org.zowe.apiml.security.common.content.CookieContentFilter;
import org.zowe.apiml.security.common.login.LoginFilter;

import java.util.Collections;
import java.util.Set;

/**
 * Security configuration for Gateway
 * <p>
 * 1. Adds Login and Query endpoints
 * 2. Allows basic and token (cookie) authentication
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {
    // List of endpoints protected by content filters
    private static final String[] PROTECTED_ENDPOINTS = {
        "/api/v1/gateway",
        "/application"
    };

    private final ObjectMapper securityObjectMapper;
    private final AuthenticationService authenticationService;
    private final AuthConfigurationProperties authConfigurationProperties;
    private final HandlerInitializer handlerInitializer;
    private final SuccessfulQueryHandler successfulQueryHandler;
    private final SuccessfulTicketHandler successfulTicketHandler;
    private final AuthProviderInitializer authProviderInitializer;
    @Qualifier("publicKeyCertificatesBase64")
    private final Set<String> publicKeyCertificatesBase64;

    @Override
    protected void configure(AuthenticationManagerBuilder auth) {
        authProviderInitializer.configure(auth);
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .headers()
            .httpStrictTransportSecurity().disable()
            .frameOptions().disable()
            .and()
            .exceptionHandling().authenticationEntryPoint(handlerInitializer.getBasicAuthUnauthorizedHandler())

            .and()
            .sessionManagement()
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)

            // login endpoint
            .and()
            .authorizeRequests()
            .antMatchers(HttpMethod.POST, authConfigurationProperties.getGatewayLoginEndpoint()).permitAll()

            // ticket endpoint
            .and()
            .authorizeRequests()
            .antMatchers(HttpMethod.POST, authConfigurationProperties.getGatewayTicketEndpoint()).authenticated()
            .and().x509()
                .x509AuthenticationFilter(apimlX509AuthenticationFilter())
                .subjectPrincipalRegex("CN=(.*?)(?:,|$)")
                .userDetailsService(x509UserDetailsService())

            // logout endpoint
            .and()
            .logout()
            .logoutRequestMatcher(new AntPathRequestMatcher(authConfigurationProperties.getGatewayLogoutEndpoint(), HttpMethod.POST.name()))
            .addLogoutHandler(logoutHandler())
            .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
            .permitAll()

            // endpoint protection
            .and()
            .authorizeRequests()
            .antMatchers("/application/health", "/application/info").permitAll()
            .antMatchers("/application/**").authenticated()

            // auth controller
            .and()
            .authorizeRequests()
            .antMatchers(
                AuthController.CONTROLLER_PATH + AuthController.ALL_PUBLIC_KEYS_PATH,
                AuthController.CONTROLLER_PATH + AuthController.CURRENT_PUBLIC_KEYS_PATH
            ).permitAll()
            .and()
            .authorizeRequests()
            .antMatchers(AuthController.CONTROLLER_PATH + AuthController.INVALIDATE_PATH, AuthController.CONTROLLER_PATH + AuthController.DISTRIBUTE_PATH).authenticated()
            .and().x509()
                .x509AuthenticationFilter(apimlX509AuthenticationFilter())
                .subjectPrincipalRegex("CN=(.*?)(?:,|$)")
                .userDetailsService(x509UserDetailsService())

            // cache controller
            .and()
            .authorizeRequests()
            .antMatchers(HttpMethod.DELETE, CacheServiceController.CONTROLLER_PATH, CacheServiceController.CONTROLLER_PATH + "/**").authenticated()
            .and().x509()
                .x509AuthenticationFilter(apimlX509AuthenticationFilter())
                .subjectPrincipalRegex("CN=(.*?)(?:,|$)")
                .userDetailsService(x509UserDetailsService())

            // add filters - login, query, ticket
            .and()
            .addFilterBefore(loginFilter(authConfigurationProperties.getGatewayLoginEndpoint()), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(queryFilter(authConfigurationProperties.getGatewayQueryEndpoint()), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(ticketFilter(authConfigurationProperties.getGatewayTicketEndpoint()), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(basicFilter(), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(cookieFilter(), UsernamePasswordAuthenticationFilter.class);
    }

    /**
     * Processes /login requests
     */
    private LoginFilter loginFilter(String loginEndpoint) throws Exception {
        return new LoginFilter(
            loginEndpoint,
            handlerInitializer.getSuccessfulLoginHandler(),
            handlerInitializer.getAuthenticationFailureHandler(),
            securityObjectMapper,
            authenticationManager(),
            handlerInitializer.getResourceAccessExceptionHandler());
    }

    /**
     * Processes /query requests
     */
    private QueryFilter queryFilter(String queryEndpoint) throws Exception {
        return new QueryFilter(
            queryEndpoint,
            successfulQueryHandler,
            handlerInitializer.getAuthenticationFailureHandler(),
            authenticationService,
            HttpMethod.GET,
            false,
            authenticationManager());
    }

    /**
     * Processes /ticket requests
     */
    private QueryFilter ticketFilter(String ticketEndpoint) throws Exception {
        return new QueryFilter(
            ticketEndpoint,
            successfulTicketHandler,
            handlerInitializer.getAuthenticationFailureHandler(),
            authenticationService,
            HttpMethod.POST,
            true,
            authenticationManager());
    }

    /**
     * Secures content with a basic authentication
     */
    private BasicContentFilter basicFilter() throws Exception {
        return new BasicContentFilter(
            authenticationManager(),
            handlerInitializer.getAuthenticationFailureHandler(),
            handlerInitializer.getResourceAccessExceptionHandler(),
            PROTECTED_ENDPOINTS);
    }

    /**
     * Secures content with a token stored in a cookie
     */
    private CookieContentFilter cookieFilter() throws Exception {
        return new CookieContentFilter(
            authenticationManager(),
            handlerInitializer.getAuthenticationFailureHandler(),
            handlerInitializer.getResourceAccessExceptionHandler(),
            authConfigurationProperties,
            PROTECTED_ENDPOINTS);
    }

    private LogoutHandler logoutHandler() {
        return (request, response, authentication) -> authenticationService.getJwtTokenFromRequest(request)
            .ifPresent(x ->
                authenticationService.invalidateJwtToken(x, true)
            );
    }

    private UserDetailsService x509UserDetailsService() {
        return username -> new User("gatewayClient", "", Collections.emptyList());
    }

    private ApimlX509AuthenticationFilter apimlX509AuthenticationFilter() throws Exception {
        ApimlX509AuthenticationFilter out = new ApimlX509AuthenticationFilter(publicKeyCertificatesBase64);
        out.setAuthenticationManager(authenticationManager());
        return out;
    }

    @Override
    public void configure(WebSecurity web) {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowUrlEncodedSlash(true);
        firewall.setAllowBackSlash(true);
        firewall.setAllowUrlEncodedPercent(true);
        firewall.setAllowUrlEncodedPeriod(true);
        firewall.setAllowSemicolon(true);
        web.httpFirewall(firewall);

        web.ignoring()
            .antMatchers(AuthController.CONTROLLER_PATH + AuthController.PUBLIC_KEYS_PATH + "/**");
    }

}
