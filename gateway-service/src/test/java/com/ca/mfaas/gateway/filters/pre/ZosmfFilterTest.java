/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package com.ca.mfaas.gateway.filters.pre;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.SERVICE_ID_KEY;

import com.ca.mfaas.gateway.security.service.AuthenticationService;
import com.netflix.zuul.context.RequestContext;

import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

public class ZosmfFilterTest {

    private static final String TOKEN = "token";
    private static final String LTPA_TOKEN = "ltpaToken";
    private static final String MY_COOKIE = "myCookie=MYCOOKIE";

    private ZosmfFilter filter;
    private AuthenticationService authenticationService;

    @Before
    public void setUp() {
        this.authenticationService = mock(AuthenticationService.class);
        this.filter = new ZosmfFilter(authenticationService);
        RequestContext ctx = RequestContext.getCurrentContext();
        ctx.clear();
        ctx.setResponse(new MockHttpServletResponse());
    }

    @Test
    public void shouldFilterZosmfRequests() {
        final RequestContext ctx = RequestContext.getCurrentContext();
        ctx.set(SERVICE_ID_KEY, "zosmftest");

        assertTrue(this.filter.shouldFilter());
    }

    @Test
    public void shouldNotFilterOtherServiceRequests() {
        final RequestContext ctx = RequestContext.getCurrentContext();
        ctx.set(SERVICE_ID_KEY, "testservice");

        assertFalse(this.filter.shouldFilter());
    }

    @Test
    public void shouldAddLtpaTokenToZosmfRequests() {
        final RequestContext ctx = RequestContext.getCurrentContext();
        ctx.set(SERVICE_ID_KEY, "zosmftest");
        when(authenticationService.getJwtTokenFromRequest(ctx.getRequest())).thenReturn(
            Optional.of(TOKEN)
        );
        when(authenticationService.getLtpaTokenFromJwtToken(TOKEN)).thenReturn(LTPA_TOKEN);

        this.filter.run();

        assertTrue(ctx.getZuulRequestHeaders().get("cookie").contains(LTPA_TOKEN));
    }

    @Test
    public void shouldPassWhenLtpaTokenIsMissing() {
        final RequestContext ctx = RequestContext.getCurrentContext();
        ctx.set(SERVICE_ID_KEY, "zosmftest");
        when(authenticationService.getJwtTokenFromRequest(ctx.getRequest())).thenReturn(
            Optional.of(TOKEN)
        );
        when(authenticationService.getLtpaTokenFromJwtToken(TOKEN)).thenReturn(null);

        this.filter.run();

        assertNull(ctx.getZuulRequestHeaders().get("cookie"));
    }

    @Test
    public void shouldPassWhenJwtTokenIsMissing() {
        final RequestContext ctx = RequestContext.getCurrentContext();
        ctx.set(SERVICE_ID_KEY, "zosmftest");
        when(authenticationService.getJwtTokenFromRequest(ctx.getRequest())).thenReturn(
            Optional.empty()
        );
        when(authenticationService.getLtpaTokenFromJwtToken(null)).thenReturn(null);

        this.filter.run();

        assertNull(ctx.getZuulRequestHeaders().get("cookie"));
    }

    @Test
    public void shouldKeepExistingCookies() {
        final RequestContext ctx = RequestContext.getCurrentContext();
        ctx.set(SERVICE_ID_KEY, "zosmftest");
        ctx.addZuulRequestHeader("Cookie", MY_COOKIE);

        when(authenticationService.getJwtTokenFromRequest(ctx.getRequest())).thenReturn(
            Optional.of(TOKEN)
        );
        when(authenticationService.getLtpaTokenFromJwtToken(TOKEN)).thenReturn(LTPA_TOKEN);

        this.filter.run();

        assertTrue(ctx.getZuulRequestHeaders().get("cookie").contains(LTPA_TOKEN));
        assertTrue(ctx.getZuulRequestHeaders().get("cookie").contains(MY_COOKIE));
    }
}
