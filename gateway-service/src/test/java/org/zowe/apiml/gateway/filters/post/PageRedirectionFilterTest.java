/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.gateway.filters.post;

import org.zowe.apiml.product.gateway.GatewayClient;
import org.zowe.apiml.product.gateway.GatewayConfigProperties;
import org.zowe.apiml.product.routing.RoutedService;
import org.zowe.apiml.product.routing.RoutedServices;
import com.netflix.util.Pair;
import com.netflix.zuul.context.RequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.zowe.apiml.product.routing.transform.TransformService;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.apache.http.HttpHeaders.LOCATION;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.SERVICE_ID_KEY;

class PageRedirectionFilterTest {

    private static final String SERVICE_ID = "discovered-service";
    private static final String TARGET_SERVER_HOST = "hostA.test.com";
    private static final int TARGET_SERVER_PORT = 8888;
    private static final String OTHER_SERVICE_ID = "other-service";
    private static final String OTHER_SERVICE_SERVER_HOST = "hostB.test.com";
    private static final int OTHER_SERVICE_SERVER_PORT = 9999;
    private static final String NOT_IN_DS_SERVER_HOST = "hostC.test.com";
    private static final int NOT_IN_DS_SERVER_PORT = 7777;

    private PageRedirectionFilter filter = null;
    private DiscoveryClient discoveryClient = null;
    private MockHttpServletResponse response = null;

    @BeforeEach
    void setUp() {
        discoveryClient = mock(DiscoveryClient.class);

        RequestContext ctx = RequestContext.getCurrentContext();
        ctx.clear();
        ctx.set(SERVICE_ID_KEY, SERVICE_ID);
        MockHttpServletRequest request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        ctx.setRequest(request);
        ctx.setResponse(response);

        GatewayConfigProperties gatewayConfigProperties = getGatewayConfigProperties(ctx);
        TransformService transformService = new TransformService(new GatewayClient(gatewayConfigProperties));

        this.filter = new PageRedirectionFilter(this.discoveryClient, transformService);
    }

    @Test
    void givenStatusCode302_whenShouldFilterCalled_thenPassFromFilter() {
        final RequestContext ctx = RequestContext.getCurrentContext();
        ctx.setResponseStatusCode(302);
        assertTrue(filter.shouldFilter());
    }


    @Test
    void sameServerAndUrlMatched() throws Exception {
        RoutedService currentService = new RoutedService("ui", "ui", "/");
        RoutedServices routedServices = new RoutedServices();
        routedServices.addRoutedService(currentService);
        this.filter.addRoutedServices(SERVICE_ID, routedServices);

        when(discoveryClient.getInstances(SERVICE_ID)).thenReturn(Collections.singletonList(
                new DefaultServiceInstance(SERVICE_ID, TARGET_SERVER_HOST, TARGET_SERVER_PORT, true)
        ));

        response.setStatus(302);
        String relativePath = "/some/path/login.html";
        String location = mockLocationSameServer(relativePath);
        final RequestContext ctx = RequestContext.getCurrentContext();
        ctx.addZuulResponseHeader(LOCATION, location);
        this.filter.run();

        Optional<Pair<String, String>> locationHeader = ctx.getZuulResponseHeaders()
                .stream()
                .filter(stringPair -> LOCATION.equals(stringPair.first()))
                .findFirst();

        verifyLocationUpdatedSameServer(locationHeader.map(Pair::second).orElse(null), location,
                "/" + SERVICE_ID + "/" + currentService.getGatewayUrl() + relativePath);
    }


    @Test
    void sameServerAndUrlNotMatched() {
        String serviceUrl = "/discoverableclient/api/v1";
        RoutedService currentService = new RoutedService("api-v1", "api/v1", serviceUrl);
        RoutedServices routedServices = new RoutedServices();
        routedServices.addRoutedService(currentService);
        this.filter.addRoutedServices(SERVICE_ID, routedServices);

        when(discoveryClient.getInstances(SERVICE_ID)).thenReturn(Collections.singletonList(
                new DefaultServiceInstance(SERVICE_ID, TARGET_SERVER_HOST, TARGET_SERVER_PORT, true)
        ));

        response.setStatus(304);
        String relativePath = "/some/path/login.html";
        String location = mockLocationSameServer(relativePath);

        final RequestContext ctx = RequestContext.getCurrentContext();
        ctx.addZuulResponseHeader(LOCATION, location);
        this.filter.run();

        Optional<Pair<String, String>> locationHeader = ctx.getZuulResponseHeaders()
                .stream()
                .filter(stringPair -> LOCATION.equals(stringPair.first()))
                .findFirst();

        verifyLocationNotUpdated(locationHeader.map(Pair::second).orElse(null), location);
    }


    @Test
    void hostRegisteredAndUrlMatched() throws Exception {
        //route for current service
        RoutedService currentService = new RoutedService("ui", "ui", "/");
        RoutedServices routedServices = new RoutedServices();
        routedServices.addRoutedService(currentService);
        this.filter.addRoutedServices(SERVICE_ID, routedServices);
        //route for other service
        String serviceUrl = "/discoverableclient/api/v1";
        RoutedService otherService = new RoutedService("ui-v1", "ui/v1", serviceUrl);
        RoutedServices otherRoutedServices = new RoutedServices();
        otherRoutedServices.addRoutedService(otherService);
        this.filter.addRoutedServices(OTHER_SERVICE_ID, otherRoutedServices);

        when(discoveryClient.getInstances(SERVICE_ID)).thenReturn(Collections.singletonList(
                new DefaultServiceInstance(SERVICE_ID, TARGET_SERVER_HOST, TARGET_SERVER_PORT, true)
        ));
        when(discoveryClient.getInstances(OTHER_SERVICE_ID)).thenReturn(Collections.singletonList(
                new DefaultServiceInstance(OTHER_SERVICE_ID, OTHER_SERVICE_SERVER_HOST, OTHER_SERVICE_SERVER_PORT, true)
        ));
        when(discoveryClient.getServices()).thenReturn(Arrays.asList(SERVICE_ID, OTHER_SERVICE_ID));

        response.setStatus(307);
        String relativePath = "/some/path/login.html";
        String location = mockLocationDSServer(serviceUrl + relativePath);

        final RequestContext ctx = RequestContext.getCurrentContext();
        ctx.addZuulResponseHeader(LOCATION, location);
        this.filter.run();

        Optional<Pair<String, String>> locationHeader = ctx.getZuulResponseHeaders()
                .stream()
                .filter(stringPair -> LOCATION.equals(stringPair.first()))
                .findFirst();

        this.verifyLocationUpdatedSameServer(locationHeader.map(Pair::second).orElse(null), location,
                "/" + OTHER_SERVICE_ID + "/" + otherService.getGatewayUrl() + relativePath);
    }


    @Test
    void differentServerAndHostPortNotInDSAndLocationContainsGatewayURL() {
        //route for current service
        RoutedService currentService = new RoutedService("ui", "ui", "/");
        RoutedServices routedServices = new RoutedServices();
        routedServices.addRoutedService(currentService);
        this.filter.addRoutedServices(SERVICE_ID, routedServices);
        //route for other service
        String serviceUrl = "/discoverableclient/api/v1";
        RoutedService otherService = new RoutedService("api-v1", "api/v1", serviceUrl);
        RoutedServices otherRoutedServices = new RoutedServices();
        otherRoutedServices.addRoutedService(otherService);
        this.filter.addRoutedServices(OTHER_SERVICE_ID, otherRoutedServices);

        when(discoveryClient.getInstances(SERVICE_ID)).thenReturn(Collections.singletonList(
                new DefaultServiceInstance(SERVICE_ID, TARGET_SERVER_HOST, TARGET_SERVER_PORT, true)
        ));
        when(discoveryClient.getInstances(OTHER_SERVICE_ID)).thenReturn(Collections.singletonList(
                new DefaultServiceInstance(OTHER_SERVICE_ID, OTHER_SERVICE_SERVER_HOST, OTHER_SERVICE_SERVER_PORT, true)
        ));
        when(discoveryClient.getServices()).thenReturn(Arrays.asList(SERVICE_ID, OTHER_SERVICE_ID));

        response.setStatus(307);
        String relativePath = "/some/path/login.html";
        String location = mockLocationOtherServer(serviceUrl + relativePath);

        final RequestContext ctx = RequestContext.getCurrentContext();
        ctx.addZuulResponseHeader(LOCATION, location);
        this.filter.run();

        Optional<Pair<String, String>> locationHeader = ctx.getZuulResponseHeaders()
                .stream()
                .filter(stringPair -> LOCATION.equals(stringPair.first()))
                .findFirst();

        this.verifyLocationNotUpdated(locationHeader.map(Pair::second).orElse(null), location);
    }


    @Test
    void serviceUrlEndWithSlash() throws Exception {
        String serviceUrl = "/discoverableclient";
        RoutedService currentService = new RoutedService("ui-v1", "ui/v1", serviceUrl + "/");
        RoutedServices routedServices = new RoutedServices();
        routedServices.addRoutedService(currentService);
        this.filter.addRoutedServices(SERVICE_ID, routedServices);

        when(discoveryClient.getInstances(SERVICE_ID)).thenReturn(Collections.singletonList(
                new DefaultServiceInstance(SERVICE_ID, TARGET_SERVER_HOST, TARGET_SERVER_PORT, true)
        ));

        response.setStatus(302);
        String relativePath = "/some/path/login.html";
        String location = mockLocationSameServer(serviceUrl + relativePath);
        final RequestContext ctx = RequestContext.getCurrentContext();
        ctx.addZuulResponseHeader(LOCATION, location);
        this.filter.run();

        Optional<Pair<String, String>> locationHeader = ctx.getZuulResponseHeaders()
                .stream()
                .filter(stringPair -> LOCATION.equals(stringPair.first()))
                .findFirst();

        verifyLocationUpdatedSameServer(locationHeader.map(Pair::second).orElse(null), location,
                "/" + SERVICE_ID + "/" + currentService.getGatewayUrl() + relativePath);
    }


    @Test
    void shouldUrlCached() throws Exception {
        //run filter the first time to put url to cache
        String serviceUrl = "/discoverableclient";
        RoutedService currentService = new RoutedService("ui-v1", "ui/v1", serviceUrl);
        RoutedServices routedServices = new RoutedServices();
        routedServices.addRoutedService(currentService);
        this.filter.addRoutedServices(SERVICE_ID, routedServices);

        when(discoveryClient.getInstances(SERVICE_ID)).thenReturn(Collections.singletonList(
                new DefaultServiceInstance(SERVICE_ID, TARGET_SERVER_HOST, TARGET_SERVER_PORT, true)
        ));

        response.setStatus(302);
        String relativePath = "/some/path/login.html";
        String location = mockLocationSameServer(serviceUrl + relativePath);
        final RequestContext ctx = RequestContext.getCurrentContext();
        ctx.addZuulResponseHeader(LOCATION, location);
        this.filter.run();

        //clear context and run filter the second time to test cache
        discoveryClient = mock(DiscoveryClient.class);
        ctx.clear();
        ctx.set(SERVICE_ID_KEY, SERVICE_ID);
        MockHttpServletRequest request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        ctx.setRequest(request);
        ctx.setResponse(response);

        when(discoveryClient.getInstances(SERVICE_ID)).thenReturn(Collections.singletonList(
                new DefaultServiceInstance(SERVICE_ID, OTHER_SERVICE_SERVER_HOST, OTHER_SERVICE_SERVER_PORT, true)
        ));

        ctx.addZuulResponseHeader(LOCATION, location);
        this.filter.run();

        Optional<Pair<String, String>> locationHeader = ctx.getZuulResponseHeaders()
                .stream()
                .filter(stringPair -> LOCATION.equals(stringPair.first()))
                .findFirst();

        verifyLocationUpdatedSameServer(locationHeader.map(Pair::second).orElse(null), location,
                "/" + SERVICE_ID + "/" + currentService.getGatewayUrl() + relativePath);
    }

    private String mockLocationSameServer(String relativeUrl) {
        return String.join("", "https://", TARGET_SERVER_HOST, ":", String.valueOf(TARGET_SERVER_PORT), relativeUrl);
    }

    private String mockLocationDSServer(String relativeUrl) {
        return String.join("", "https://", OTHER_SERVICE_SERVER_HOST, ":", String.valueOf(OTHER_SERVICE_SERVER_PORT), relativeUrl);
    }

    private String mockLocationOtherServer(String relativeUrl) {
        return String.join("", "https://", NOT_IN_DS_SERVER_HOST, ":", String.valueOf(NOT_IN_DS_SERVER_PORT), relativeUrl);
    }

    private void verifyLocationUpdatedSameServer(String actualLocation, String originalLocation, String relativeUrl) throws Exception {
        RequestContext ctx = RequestContext.getCurrentContext();
        URI uri = new URI(originalLocation);
        uri = new URI(ctx.getRequest().getScheme(), uri.getUserInfo(), ctx.getRequest().getLocalName(), ctx.getRequest().getLocalPort(),
                relativeUrl, uri.getQuery(), uri.getFragment());
        assertEquals(uri.toString(), actualLocation, "Location header is not updated as expected");
    }

    private void verifyLocationNotUpdated(String actualLocation, String expectedLocation) {
        assertEquals(expectedLocation, actualLocation, "Location should not be updated");
    }

    private GatewayConfigProperties getGatewayConfigProperties(RequestContext ctx) {
        return GatewayConfigProperties.builder()
                .scheme(ctx.getRequest().getScheme())
                .hostname(ctx.getRequest().getLocalName() + ":" + ctx.getRequest().getLocalPort())
                .build();
    }
}
