/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.gateway.security.service.schema;

import com.netflix.zuul.context.RequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.zowe.apiml.gateway.security.service.PassTicketException;
import org.zowe.apiml.gateway.utils.CleanCurrentRequestContextTest;
import org.zowe.apiml.passticket.PassTicketService;
import org.zowe.apiml.security.common.auth.Authentication;
import org.zowe.apiml.security.common.auth.AuthenticationScheme;
import org.zowe.apiml.security.common.config.AuthConfigurationProperties;
import org.zowe.apiml.security.common.token.QueryResponse;

import javax.servlet.http.HttpServletRequest;
import java.util.Calendar;

import static org.junit.jupiter.api.Assertions.*;
import static org.zowe.apiml.passticket.PassTicketService.DefaultPassTicketImpl.UNKNOWN_USER;

public class HttpBasicPassTicketSchemeTest extends CleanCurrentRequestContextTest {

    private static final String USERNAME = "USERNAME";
    private final AuthConfigurationProperties authConfigurationProperties = new AuthConfigurationProperties();
    private HttpBasicPassTicketScheme httpBasicPassTicketScheme;

    @BeforeEach
    public void init() {
        PassTicketService passTicketService = new PassTicketService();
        httpBasicPassTicketScheme = new HttpBasicPassTicketScheme(passTicketService, authConfigurationProperties);
    }

    @Test
    public void testCreateCommand() {
        Calendar calendar = Calendar.getInstance();
        Authentication authentication = new Authentication(AuthenticationScheme.HTTP_BASIC_PASSTICKET, "APPLID");
        QueryResponse queryResponse = new QueryResponse("domain", USERNAME, calendar.getTime(), calendar.getTime(), QueryResponse.Source.ZOWE);

        AuthenticationCommand ac = httpBasicPassTicketScheme.createCommand(authentication, () -> queryResponse);
        assertNotNull(ac);

        RequestContext requestContext = new RequestContext();
        HttpServletRequest request = new MockHttpServletRequest();
        requestContext.setRequest(request);
        RequestContext.testSetCurrentContext(requestContext);
        ac.apply(null);

        assertEquals("Basic VVNFUk5BTUU6Wk9XRV9EVU1NWV9QQVNTX1RJQ0tFVF9BUFBMSURfVVNFUk5BTUVfMA==",  // USERNAME:ZOWE_DUMMY_PASS_TICKET_APPLID_USERNAME_0
            requestContext.getZuulRequestHeaders().get("authorization"));

        // JWT token expired one minute ago (command expired also if JWT token expired)
        calendar.add(Calendar.MINUTE, -1);
        queryResponse2 = new QueryResponse("domain", USERNAME, calendar.getTime(), calendar.getTime(), QueryResponse.Source.ZOWE);
        ac = httpBasicPassTicketScheme.createCommand(authentication, queryResponse);
        assertTrue(ac.isExpired());

        // JWT token will expire in one minute (command expired also if JWT token expired)
        calendar.add(Calendar.MINUTE, 2);
        queryResponse3 = new QueryResponse("domain", USERNAME, calendar.getTime(), calendar.getTime(), QueryResponse.Source.ZOWE);
        ac = httpBasicPassTicketScheme.createCommand(authentication, queryResponse);
        assertFalse(ac.isExpired());

        calendar.add(Calendar.MINUTE, 100);
        queryResponse4 = new QueryResponse("domain", USERNAME, calendar.getTime(), calendar.getTime(), QueryResponse.Source.ZOWE);
        ac = httpBasicPassTicketScheme.createCommand(authentication, queryResponse);

        calendar = Calendar.getInstance();
        calendar.add(Calendar.SECOND, authConfigurationProperties.getPassTicket().getTimeout());
        // checking setup of expired time, JWT expired in future (more than hour), check if set date is similar to passticket timeout (5s)
        assertEquals(0.0, Math.abs(calendar.getTime().getTime() - (long) ReflectionTestUtils.getField(ac, "expireAt")), 10.0);
    }

    @Test
    public void returnsCorrectScheme() {
        assertEquals(AuthenticationScheme.HTTP_BASIC_PASSTICKET, httpBasicPassTicketScheme.getScheme());
    }

    @Test
    public void getExceptionWhenUserIdNotValid() {
        String applId = "APPLID";

        Calendar calendar = Calendar.getInstance();
        Authentication authentication = new Authentication(AuthenticationScheme.HTTP_BASIC_PASSTICKET, applId);
        QueryResponse queryResponse = new QueryResponse("domain", UNKNOWN_USER, calendar.getTime(), calendar.getTime(), QueryResponse.Source.ZOWE);
        Exception exception = assertThrows(PassTicketException.class,
            () -> httpBasicPassTicketScheme.createCommand(authentication, () -> queryResponse),
            "Expected exception is not AuthenticationException");
        assertEquals((String.format("Could not generate PassTicket for user ID %s and APPLID %s", UNKNOWN_USER, applId)), exception.getMessage());
    }

    @Test
    public void testIsRequiredValidJwt() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, 1);
        Authentication authentication = new Authentication(AuthenticationScheme.HTTP_BASIC_PASSTICKET, "applid");
        QueryResponse queryResponse = new QueryResponse("domain", "username", calendar.getTime(), calendar.getTime(), QueryResponse.Source.ZOWE);
        AuthenticationCommand ac = httpBasicPassTicketScheme.createCommand(authentication, () -> queryResponse);
        assertTrue(ac.isRequiredValidJwt());
    }

}
