/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.acceptance.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.netflix.zuul.filters.ZuulProperties;
import org.springframework.cloud.netflix.zuul.filters.discovery.ServiceRouteMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zowe.apiml.acceptance.netflix.ApimlRouteLocatorStub;
import org.zowe.apiml.acceptance.netflix.ApplicationRegistry;
import org.zowe.apiml.zaas.filters.post.PageRedirectionFilter;
import org.zowe.apiml.zaas.filters.pre.LocationFilter;
import org.zowe.apiml.zaas.ws.WebSocketProxyServerHandler;
import org.zowe.apiml.product.routing.RoutedServicesUser;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class ApimlRoutingConfig {

    @Bean
    @Autowired
    public ApimlRouteLocatorStub discoveryClientRouteLocator(DiscoveryClient discovery,
                                                             ZuulProperties zuulProperties,
                                                             ServiceRouteMapper serviceRouteMapper,
                                                             WebSocketProxyServerHandler webSocketProxyServerHandler,
                                                             PageRedirectionFilter pageRedirectionFilter,
                                                             ApplicationRegistry applicationRegistry,
                                                             LocationFilter locationFilter
    ) {
        List<RoutedServicesUser> routedServicesUsers = new ArrayList<>();
        routedServicesUsers.add(locationFilter);
        routedServicesUsers.add(webSocketProxyServerHandler);
        routedServicesUsers.add(pageRedirectionFilter);
        zuulProperties.setDecodeUrl(false);

        return new ApimlRouteLocatorStub("", discovery, zuulProperties, serviceRouteMapper, routedServicesUsers, applicationRegistry);
    }

}
