/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.gateway.config;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.LeaseInfo;
import com.netflix.appinfo.MyDataCenterInfo;
import com.netflix.discovery.shared.transport.jersey.EurekaJerseyClientImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.netflix.eureka.EurekaClientConfigBean;
import org.springframework.context.ApplicationContext;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.zowe.apiml.product.constants.CoreService.GATEWAY;

class DiscoveryClientBeanTest {
    DiscoveryClientConfig dcConfig;

    @BeforeEach
    void setup() {
        ApplicationContext context = mock(ApplicationContext.class);
        EurekaJerseyClientImpl.EurekaJerseyClientBuilder builder = mock(EurekaJerseyClientImpl.EurekaJerseyClientBuilder.class);
        dcConfig = new DiscoveryClientConfig(context, null, builder);
    }

    @Test
    void givenListOfAdditionalRegistrations_thenCreateNewDiscoveryClientForEach() {
        List<AdditionalRegistration> additionalRegistrations = Arrays.asList(
            AdditionalRegistration.builder().discoveryServiceUrls("https://host:10021/eureka").build(),
            AdditionalRegistration.builder().discoveryServiceUrls("https://host:10011/eureka").build());


        ApplicationInfoManager manager = mock(ApplicationInfoManager.class);
        InstanceInfo info = new InstanceInfo.Builder(mock(InstanceInfo.class)).setAppName(GATEWAY.name()).build();

        when(manager.getInfo()).thenReturn(info);
        when(info.getIPAddr()).thenReturn("127.0.0.1");

        when(info.getDataCenterInfo()).thenReturn(new MyDataCenterInfo(DataCenterInfo.Name.MyOwn));
        LeaseInfo leaseInfo = mock(LeaseInfo.class);

        when(info.getLeaseInfo()).thenReturn(leaseInfo);

        EurekaClientConfigBean bean = new EurekaClientConfigBean();
        DiscoveryClientWrapper wrapper = dcConfig.additionalDiscoveryClientWrapper(manager, bean, null, additionalRegistrations);
        wrapper.shutdown();

        assertThat(wrapper.getDiscoveryClients()).hasSize(2);
    }
}
