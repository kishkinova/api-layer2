/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.acceptance.config.ribbon;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.*;
import lombok.RequiredArgsConstructor;
import org.apache.http.impl.client.CloseableHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.context.named.NamedContextFactory;
import org.springframework.cloud.netflix.ribbon.*;
import org.springframework.cloud.netflix.ribbon.apache.RibbonLoadBalancingHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.MapPropertySource;
import org.zowe.apiml.gateway.context.ConfigurableNamedContextFactory;
import org.zowe.apiml.gateway.metadata.service.LoadBalancerRegistry;
import org.zowe.apiml.gateway.ribbon.AbortingRetryListener;
import org.zowe.apiml.gateway.ribbon.ApimlLoadBalancer;
import org.zowe.apiml.gateway.ribbon.ApimlRetryableClient;
import org.zowe.apiml.gateway.ribbon.ApimlRibbonRetryFactory;
import org.zowe.apiml.gateway.ribbon.loadbalancer.InstanceInfoExtractor;
import org.zowe.apiml.gateway.ribbon.loadbalancer.LoadBalancerConstants;
import org.zowe.apiml.gateway.ribbon.loadbalancer.LoadBalancerRuleAdapter;
import org.zowe.apiml.gateway.ribbon.loadbalancer.LoadBalancingPredicatesRibbonConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration of client side load balancing with Ribbon
 */
@Configuration
@RequiredArgsConstructor
public class RibbonTestConfiguration {
    private final PropertiesFactory propertiesFactory;

    @RibbonClientName
    private String ribbonClientName = "client";

    @Bean
    public ApimlRibbonRetryFactory apimlRibbonRetryFactory(SpringClientFactory springClientFactory) {
        AbortingRetryListener retryListener = new AbortingRetryListener();
        return new ApimlRibbonRetryFactory(springClientFactory, retryListener);
    }

    @Bean
    @Primary
    @Autowired
    public RibbonLoadBalancingHttpClient ribbonLoadBalancingHttpClient(
        @Qualifier("mockProxy") CloseableHttpClient httpClientProxy,
        IClientConfig config,
        ServerIntrospector serverIntrospector,
        ApimlRibbonRetryFactory retryFactory,
        RibbonLoadBalancerContext ribbonContext
    ) {
        ApimlRetryableClient client = new ApimlRetryableClient(
            httpClientProxy, config, serverIntrospector, retryFactory);
        client.setRibbonLoadBalancerContext(ribbonContext);
        return client;
    }


    @Bean
    @Primary
    @Autowired
    public ILoadBalancer ribbonLoadBalancer(IClientConfig config,
                                            ServerList<Server> serverList, ServerListFilter<Server> serverListFilter,
                                            IPing ping, ServerListUpdater serverListUpdater,
                                            LoadBalancerRegistry loadBalancerRegistry, ConfigurableNamedContextFactory<NamedContextFactory.Specification> predicateFactory) {
        if (this.propertiesFactory.isSet(ILoadBalancer.class, ribbonClientName)) {
            return this.propertiesFactory.get(ILoadBalancer.class, config, ribbonClientName);
        }

        InstanceInfoExtractor infoExtractor = new InstanceInfoExtractor(serverList.getInitialListOfServers());

        InstanceInfo randomInstanceInfo = infoExtractor.getInstanceInfo().orElseThrow(() -> new IllegalStateException("Not able to retrieve InstanceInfo from server list, Load balancing is not available"));

        Map<String, Object> metadataMap = new HashMap<>();
        randomInstanceInfo.getMetadata().forEach(
            (key, value) -> metadataMap.put(LoadBalancerConstants.getMetadataPrefix() + key, value)
        );

        predicateFactory.addInitializer(randomInstanceInfo.getAppName(), context ->
            context.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("InstanceInfoMetadata", metadataMap))
        );

        IRule rule = new LoadBalancerRuleAdapter(
            randomInstanceInfo,
            predicateFactory, config);

        return new ApimlLoadBalancer<>(config, rule, ping, serverList,
            serverListFilter, serverListUpdater, loadBalancerRegistry);
    }

    @Bean
    public ConfigurableNamedContextFactory<NamedContextFactory.Specification> predicateFactory() {
        return new ConfigurableNamedContextFactory<>(LoadBalancingPredicatesRibbonConfig.class, "contextConfiguration",
            LoadBalancerConstants.INSTANCE_KEY + LoadBalancerConstants.SERVICEID_KEY);
    }

}
