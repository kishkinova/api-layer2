/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.apicatalog.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.preauth.x509.X509AuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.zowe.apiml.filter.AttlsFilter;
import org.zowe.apiml.filter.SecureConnectionFilter;
import org.zowe.apiml.security.client.EnableApimlAuth;
import org.zowe.apiml.security.client.login.GatewayLoginProvider;
import org.zowe.apiml.security.client.token.GatewayTokenProvider;
import org.zowe.apiml.security.common.config.AuthConfigurationProperties;
import org.zowe.apiml.security.common.config.CertificateAuthenticationProvider;
import org.zowe.apiml.security.common.config.HandlerInitializer;
import org.zowe.apiml.security.common.content.BasicContentFilter;
import org.zowe.apiml.security.common.content.BearerContentFilter;
import org.zowe.apiml.security.common.content.CookieContentFilter;
import org.zowe.apiml.security.common.filter.CategorizeCertsFilter;
import org.zowe.apiml.security.common.login.LoginFilter;
import org.zowe.apiml.security.common.login.ShouldBeAlreadyAuthenticatedFilter;

import java.util.Collections;
import java.util.Set;

/**
 * Main configuration class of Spring web security for Api Catalog
 * binds authentication managers
 * configures ignores for static content
 * adds endpoints and secures them
 * adds security filters
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableApimlAuth
public class SecurityConfiguration {
    private static final String APIDOC_ROUTES = "/apidoc/**";
    private static final String STATIC_REFRESH_ROUTE = "/static-api/refresh";

    private final ObjectMapper securityObjectMapper;
    private final AuthConfigurationProperties authConfigurationProperties;
    private final HandlerInitializer handlerInitializer;
    private final GatewayLoginProvider gatewayLoginProvider;
    private final GatewayTokenProvider gatewayTokenProvider;
    @Qualifier("publicKeyCertificatesBase64")
    private final Set<String> publicKeyCertificatesBase64;
    @Value("${server.attls.enabled:false}")
    private boolean isAttlsEnabled;

    @Value("${apiml.metrics.enabled:false}")
    private boolean isMetricsEnabled;

    /**
     * Filter chain for protecting /apidoc/** endpoints with MF credentials for client certificate.
     */
    @Configuration
    @Order(1)
    public class FilterChainBasicAuthOrTokenOrCertForApiDoc {

        @Value("${apiml.security.ssl.verifySslCertificatesOfServices:true}")
        private boolean verifySslCertificatesOfServices;

        @Value("${apiml.security.ssl.nonStrictVerifySslCertificatesOfServices:false}")
        private boolean nonStrictVerifySslCertificatesOfServices;

        @Bean
        public SecurityFilterChain basicAuthOrTokenOrCertApiDocFilterChain(HttpSecurity http) throws Exception {
            mainframeCredentialsConfiguration(
                baseConfiguration(http.requestMatchers().antMatchers(APIDOC_ROUTES, STATIC_REFRESH_ROUTE).and())
            )
                .authorizeRequests()
                .antMatchers(APIDOC_ROUTES, STATIC_REFRESH_ROUTE).authenticated()
                .and()
                .authenticationProvider(gatewayLoginProvider)
                .authenticationProvider(gatewayTokenProvider)
                .authenticationProvider(new CertificateAuthenticationProvider());

            if (verifySslCertificatesOfServices || nonStrictVerifySslCertificatesOfServices) {
                if (isAttlsEnabled) {
                    http.x509()
                        .userDetailsService(x509UserDetailsService())
                        .and()
                        .addFilterBefore(reversedCategorizeCertFilter(), X509AuthenticationFilter.class)
                        .addFilterBefore(new AttlsFilter(), X509AuthenticationFilter.class)
                        .addFilterBefore(new SecureConnectionFilter(), AttlsFilter.class);
                } else {
                    http.x509()
                        .userDetailsService(x509UserDetailsService());
                }
            }

            return http.build();
        }

        private UserDetailsService x509UserDetailsService() {
            return username -> new User(username, "", Collections.emptyList());
        }

        private CategorizeCertsFilter reversedCategorizeCertFilter() {
            CategorizeCertsFilter out = new CategorizeCertsFilter(publicKeyCertificatesBase64);
            out.setCertificateForClientAuth(crt -> out.getPublicKeyCertificatesBase64().contains(out.base64EncodePublicKey(crt)));
            out.setNotCertificateForClientAuth(crt -> !out.getPublicKeyCertificatesBase64().contains(out.base64EncodePublicKey(crt)));
            return out;
        }
    }

    /**
     * Default filter chain to protect all routes with MF credentials.
     */
    @Configuration
    public class FilterChainBasicAuthOrTokenAllEndpoints {

        @Bean
        public WebSecurityCustomizer webSecurityCustomizer() {
            String[] noSecurityAntMatchers = {
                "/",
                "/static/**",
                "/favicon.ico",
                "/api-doc"
            };
            return web -> web.ignoring().antMatchers(noSecurityAntMatchers);
        }

        @Bean
        public SecurityFilterChain basicAuthOrTokenAllEndpointsFilterChain(HttpSecurity http) throws Exception {
            mainframeCredentialsConfiguration(baseConfiguration(http))
                .authorizeRequests()
                .antMatchers("/static-api/**").authenticated()
                .antMatchers("/containers/**").authenticated()
                .antMatchers(APIDOC_ROUTES).authenticated()
                .antMatchers("/application/health", "/application/info").permitAll()
                .and()
                .authenticationProvider(gatewayLoginProvider)
                .authenticationProvider(gatewayTokenProvider);

            if (isMetricsEnabled) {
                http.authorizeRequests().antMatchers("/application/hystrixstream").permitAll();
            }

            http.authorizeRequests().antMatchers("/application/**").authenticated();

            if (isAttlsEnabled) {
                http.addFilterBefore(new SecureConnectionFilter(), BasicContentFilter.class);
            }
            return http.build();
        }
    }

    private HttpSecurity baseConfiguration(HttpSecurity http) throws Exception {
        http
            .csrf().disable()   // NOSONAR
            .headers()
            .httpStrictTransportSecurity().disable()
            .frameOptions().disable()
            .and()
            .exceptionHandling()

            .defaultAuthenticationEntryPointFor(
                handlerInitializer.getBasicAuthUnauthorizedHandler(), new AntPathRequestMatcher("/application/**")
            )
            .defaultAuthenticationEntryPointFor(
                handlerInitializer.getBasicAuthUnauthorizedHandler(), new AntPathRequestMatcher(APIDOC_ROUTES)
            )
            .defaultAuthenticationEntryPointFor(
                handlerInitializer.getBasicAuthUnauthorizedHandler(), new AntPathRequestMatcher(STATIC_REFRESH_ROUTE)
            )
            .defaultAuthenticationEntryPointFor(
                handlerInitializer.getUnAuthorizedHandler(), new AntPathRequestMatcher("/**")
            )

            .and()
            .sessionManagement()
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS);

        return http;
    }

    private HttpSecurity mainframeCredentialsConfiguration(HttpSecurity http) throws Exception {
        http
            // login endpoint
            .authorizeRequests()
            .antMatchers(HttpMethod.POST, authConfigurationProperties.getServiceLoginEndpoint()).permitAll()

            // logout endpoint
            .and()
            .logout()
            .logoutUrl(authConfigurationProperties.getServiceLogoutEndpoint())
            .logoutSuccessHandler(logoutSuccessHandler())
            .and().apply(new CustomSecurityFilters());

        return http;
    }

    private class CustomSecurityFilters extends AbstractHttpConfigurer<CustomSecurityFilters, HttpSecurity> {
        @Override
        public void configure(HttpSecurity http) throws Exception {
            AuthenticationManager authenticationManager = http.getSharedObject(AuthenticationManager.class);

            http.addFilterBefore(new ShouldBeAlreadyAuthenticatedFilter(authConfigurationProperties.getServiceLoginEndpoint(), handlerInitializer.getAuthenticationFailureHandler()), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(loginFilter(authConfigurationProperties.getServiceLoginEndpoint(), authenticationManager), ShouldBeAlreadyAuthenticatedFilter.class)
                .addFilterBefore(basicFilter(authenticationManager), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(cookieFilter(authenticationManager), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(bearerContentFilter(authenticationManager), UsernamePasswordAuthenticationFilter.class);
        }

        private LoginFilter loginFilter(String loginEndpoint, AuthenticationManager authenticationManager) {
            return new LoginFilter(
                loginEndpoint,
                handlerInitializer.getSuccessfulLoginHandler(),
                handlerInitializer.getAuthenticationFailureHandler(),
                securityObjectMapper,
                authenticationManager,
                handlerInitializer.getResourceAccessExceptionHandler()
            );
        }

        /**
         * Secures content with a basic authentication
         */
        private BasicContentFilter basicFilter(AuthenticationManager authenticationManager) {
            return new BasicContentFilter(
                authenticationManager,
                handlerInitializer.getAuthenticationFailureHandler(),
                handlerInitializer.getResourceAccessExceptionHandler()
            );
        }

        /**
         * Secures content with a token stored in a cookie
         */
        private CookieContentFilter cookieFilter(AuthenticationManager authenticationManager) {
            return new CookieContentFilter(
                authenticationManager,
                handlerInitializer.getAuthenticationFailureHandler(),
                handlerInitializer.getResourceAccessExceptionHandler(),
                authConfigurationProperties);
        }

        /**
         * Secures content with a Bearer token
         */
        private BearerContentFilter bearerContentFilter(AuthenticationManager authenticationManager) {
            return new BearerContentFilter(
                authenticationManager,
                handlerInitializer.getAuthenticationFailureHandler(),
                handlerInitializer.getResourceAccessExceptionHandler()
            );
        }
    }

    @Bean
    public LogoutSuccessHandler logoutSuccessHandler() {
        return new ApiCatalogLogoutSuccessHandler(authConfigurationProperties);
    }
}
