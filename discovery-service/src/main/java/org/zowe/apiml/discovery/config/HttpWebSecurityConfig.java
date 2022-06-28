/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.discovery.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.zowe.apiml.security.common.config.HandlerInitializer;
import org.zowe.apiml.security.common.content.BasicContentFilter;

/**
 * Main class configuring Spring security for Discovery Service
 * <p>
 * This configuration is applied if "https" Spring profile is not active
 */
@Configuration
@ComponentScan({
    "org.zowe.apiml.security.common",
    "org.zowe.apiml.gateway.security.login"
})
@EnableWebSecurity
@RequiredArgsConstructor
@Profile({"!https", "!attls"})
public class HttpWebSecurityConfig extends AbstractWebSecurityConfigurer {
    private static final String DISCOVERY_REALM = "API Mediation Discovery Service realm";

    @Value("${apiml.discovery.userid:eureka}")
    private String eurekaUserid;

    @Value("${apiml.discovery.password:password}")
    private String eurekaPassword;

    @Value("${apiml.metrics.enabled:false}")
    private boolean isMetricsEnabled;

    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
        auth.inMemoryAuthentication().withUser(eurekaUserid).password("{noop}" + eurekaPassword).roles("EUREKA");
    }

    private final HandlerInitializer handlerInitializer;

    @Bean
    public WebSecurityCustomizer httpWebSecurityCustomizer() {
        String[] noSecurityAntMatchers = {
            "/favicon.ico",
            "/eureka/css/**",
            "/eureka/js/**",
            "/eureka/fonts/**",
            "/eureka/images/**"
        };
        return web -> web.ignoring().antMatchers(noSecurityAntMatchers);
    }

    @Bean
    public SecurityFilterChain httpFilterChain(HttpSecurity http) throws Exception {
        baseConfigure(http)
            .httpBasic().realmName(DISCOVERY_REALM)
            .and()
            .authorizeRequests()
            .antMatchers("/application/info", "/application/health").permitAll()
            .antMatchers("/**").authenticated();

        if (isMetricsEnabled) {
            http.authorizeRequests().antMatchers("/application/hystrix.stream").permitAll();
        }

        return http.apply(new CustomSecurityFilters()).and().build();
    }

    private class CustomSecurityFilters extends AbstractHttpConfigurer<CustomSecurityFilters, HttpSecurity> {
        @Override
        public void configure(HttpSecurity http) {
            AuthenticationManager authenticationManager = http.getSharedObject(AuthenticationManager.class);
            http.addFilterBefore(basicFilter(authenticationManager), UsernamePasswordAuthenticationFilter.class);
        }

        private BasicContentFilter basicFilter(AuthenticationManager authenticationManager) {
            return new BasicContentFilter(authenticationManager, handlerInitializer.getAuthenticationFailureHandler(), handlerInitializer.getResourceAccessExceptionHandler());
        }
    }
}
