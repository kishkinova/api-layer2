/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.gateway.routing;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.netflix.zuul.filters.ZuulProperties;
import org.springframework.cloud.netflix.zuul.filters.discovery.DiscoveryClientRouteLocator;
import org.springframework.cloud.netflix.zuul.filters.discovery.ServiceRouteMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zowe.apiml.gateway.filters.post.ConvertAuthTokenInUriToCookieFilter;
import org.zowe.apiml.gateway.filters.post.PageRedirectionFilter;
import org.zowe.apiml.gateway.filters.pre.*;
import org.zowe.apiml.gateway.ws.WebSocketProxyServerHandler;
import org.zowe.apiml.message.core.MessageService;
import org.zowe.apiml.product.routing.RoutedServicesUser;
import org.zowe.apiml.product.routing.transform.TransformService;
import org.zowe.apiml.security.common.config.AuthConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class ApimlRoutingConfig {

    @Bean
    public LocationFilter locationFilter() {
        return new LocationFilter();
    }

    @Bean
    public EncodedCharactersFilter encodedCharactersFilter(DiscoveryClient discovery,
                                                                 MessageService messageService) {
        return new EncodedCharactersFilter(discovery, messageService);
    }

    @Bean
    public SlashFilter slashFilter() {
        return new SlashFilter();
    }

    @Bean
    public ServiceAuthenticationFilter serviceAuthenticationFilter() {
        return new ServiceAuthenticationFilter();
    }

    @Bean
    public ServiceNotFoundFilter serviceNotFoundFilter() {
        return new ServiceNotFoundFilter(
            new RequestContextProviderThreadLocal()
        );
    }

    @Bean
    public PageRedirectionFilter pageRedirectionFilter(DiscoveryClient discovery,
                                                       TransformService transformService) {
        return new PageRedirectionFilter(discovery, transformService);
    }

    @Bean
    @Autowired
    public ConvertAuthTokenInUriToCookieFilter convertAuthTokenInUriToCookieFilter(AuthConfigurationProperties authConfigurationProperties) {
        return new ConvertAuthTokenInUriToCookieFilter(authConfigurationProperties);
    }

    @Bean
    @ConditionalOnProperty(name = "apiml.routing.mode", havingValue = "default")
    @Autowired
    public DiscoveryClientRouteLocator discoveryClientRouteLocator(DiscoveryClient discovery,
                                                                   ZuulProperties zuulProperties,
                                                                   ServiceRouteMapper serviceRouteMapper,
                                                                   WebSocketProxyServerHandler webSocketProxyServerHandler,
                                                                   PageRedirectionFilter pageRedirectionFilter) {
        List<RoutedServicesUser> routedServicesUsers = new ArrayList<>();
        routedServicesUsers.add(locationFilter());
        routedServicesUsers.add(webSocketProxyServerHandler);
        routedServicesUsers.add(pageRedirectionFilter);

        return new ApimlRouteLocator("", discovery, zuulProperties, serviceRouteMapper, routedServicesUsers);
    }

    @Bean
    @ConditionalOnProperty(name = "apiml.routing.mode", havingValue = "new")
    @Autowired
    public DiscoveryClientRouteLocator apimlClientRouteLocator(DiscoveryClient discoveryClient,
                                                ZuulProperties zuulProperties,
                                                RoutedServicesNotifier routedServicesNotifier) {
        return new NewApimlRouteLocator("", zuulProperties, discoveryClient, routedServicesNotifier);
    }

    @Bean
    @ConditionalOnProperty(name = "apiml.routing.mode", havingValue = "new")
    @Autowired
    public RoutedServicesNotifier routedServicesNotifier(List<RoutedServicesUser> routedServicesUserList) {
        return new RoutedServicesNotifier(routedServicesUserList);
    }

}
