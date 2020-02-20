/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.gateway.filters.pre;

import com.netflix.zuul.context.RequestContext;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.zowe.apiml.message.core.MessageService;
import org.zowe.apiml.message.yaml.YamlMessageService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.*;



@RunWith(MockitoJUnitRunner.class)
public class EncodedCharactersFilterTest {

    private EncodedCharactersFilter filter;

    private final String METADATA_KEY = EncodedCharactersFilter.METADATA_KEY;
    private final String SERVICE_ID = "serviceid";

    private final DefaultServiceInstance serviceInstanceWithConfiguration = new DefaultServiceInstance("INSTANCE1", SERVICE_ID ,"",0,true, new HashMap<String, String>());
    private final DefaultServiceInstance serviceInstanceWithoutConfiguration = new DefaultServiceInstance("INSTANCE2", SERVICE_ID ,"",0,true, new HashMap<String, String>());

    private static MessageService messageService;

    @Mock
    DiscoveryClient discoveryClient;

    @BeforeClass
    public static void initMessageService() {
        messageService = new YamlMessageService("/gateway-log-messages.yml");
    }

    @Before
    public void setup() {
        filter = new EncodedCharactersFilter(discoveryClient, messageService);
        serviceInstanceWithConfiguration.getMetadata().put(METADATA_KEY, "true");
        serviceInstanceWithoutConfiguration.getMetadata().put(METADATA_KEY, "false");
        RequestContext ctx = RequestContext.getCurrentContext();
        ctx.clear();
        ctx.set(PROXY_KEY, "api/v1/" + SERVICE_ID);
        ctx.set(SERVICE_ID_KEY, SERVICE_ID);
        ctx.setResponse(new MockHttpServletResponse());
    }

    @Test
    public void givenSingleInstance_WhenNotConfigured_ShouldFilter() {
        List<ServiceInstance> instanceList = new ArrayList<>();
        instanceList.add(serviceInstanceWithoutConfiguration);
        when(discoveryClient.getInstances(SERVICE_ID)).thenReturn(instanceList);

        assertThat(filter.shouldFilter(), is(equalTo(true)));
    }

    @Test
    public void givenSingleInstance_WhenConfigured_ShouldNotFilter() {
        List<ServiceInstance> instanceList = new ArrayList<>();
        instanceList.add(serviceInstanceWithConfiguration);
        when(discoveryClient.getInstances(SERVICE_ID)).thenReturn(instanceList);

        assertThat(filter.shouldFilter(), is(equalTo(false)));
    }

    @Test
    public void shouldReturnFilterType() {
        String filterType = this.filter.filterType();
        assertEquals("pre", filterType);
    }

    @Test
    public void shouldReturnFilterOrder() {
        int filterOrder = this.filter.filterOrder();
        assertEquals(6, filterOrder);
    }

    @Test
    public void givenMultipleInstances_WhenMixedSetup_ShouldBePesimistic() {
        List<ServiceInstance> instanceList = new ArrayList<>();
        instanceList.add(serviceInstanceWithoutConfiguration);
        instanceList.add(serviceInstanceWithConfiguration);
        when(discoveryClient.getInstances(SERVICE_ID)).thenReturn(instanceList);

        assertThat(filter.shouldFilter(), is(equalTo(true)));
    }

    @Test
    public void shouldRejectRequestsWithEncodedCharacters() {
        RequestContext context = RequestContext.getCurrentContext();
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setRequestURI("/He%2f%2f0%2dwor%2fd");
        context.setRequest(mockRequest);
        this.filter.run();
        assertTrue(context.getResponseBody().contains("Service 'serviceid' does not allow encoded characters used in request path: '/He%2f%2f0%2dwor%2fd'."));
        assertTrue(context.getResponseBody().contains("ZWEAG701E"));
        assertEquals(400, context.getResponse().getStatus());
    }

    @Test
    public void shouldAllowRequestsWithoutEncodedCharacters() {
        RequestContext context = RequestContext.getCurrentContext();
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setRequestURI("/HelloWorld");
        context.setRequest(mockRequest);

        this.filter.run();

        assertEquals(200, context.getResponse().getStatus());
    }

    @Test
    public void shouldPassNullRequest() {
        RequestContext context = RequestContext.getCurrentContext();
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setRequestURI(null);
        context.setRequest(mockRequest);

        this.filter.run();

        assertEquals(200, context.getResponse().getStatus());
    }
}
