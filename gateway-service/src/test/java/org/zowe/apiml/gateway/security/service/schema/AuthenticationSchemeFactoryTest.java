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
import org.junit.jupiter.api.Test;
import org.zowe.apiml.gateway.security.service.schema.source.AuthSourceService;
import org.zowe.apiml.gateway.security.service.schema.source.JwtAuthSource;
import org.zowe.apiml.gateway.utils.CleanCurrentRequestContextTest;
import org.zowe.apiml.auth.Authentication;
import org.zowe.apiml.auth.AuthenticationScheme;
import org.zowe.apiml.security.common.token.QueryResponse;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthenticationSchemeFactoryTest extends CleanCurrentRequestContextTest {

    private static final AuthenticationCommand COMMAND = mock(AuthenticationCommand.class);

    private AbstractAuthenticationScheme createScheme(final AuthenticationScheme scheme, final boolean isDefault) {
        return new AbstractAuthenticationScheme() {
            @Override
            public AuthenticationScheme getScheme() {
                return scheme;
            }

            @Override
            public boolean isDefault() {
                return isDefault;
            }

            @Override
            public AuthenticationCommand createCommand(Authentication authentication, JwtAuthSource authSource) {
                return COMMAND;
            }
        };
    }

    @Test
    void testInit_OK() {
        assertDoesNotThrow(() -> {
            new AuthenticationSchemeFactory(
                mock(AuthSourceService.class),
                Arrays.asList(
                    createScheme(AuthenticationScheme.BYPASS, true),
                    createScheme(AuthenticationScheme.HTTP_BASIC_PASSTICKET, false),
                    createScheme(AuthenticationScheme.ZOWE_JWT, false)
                )
            );
        });
    }

    @Test
    void testInit_NoDefault() {
        List<AbstractAuthenticationScheme> schemes = Arrays.asList(
            createScheme(AuthenticationScheme.BYPASS, false),
            createScheme(AuthenticationScheme.HTTP_BASIC_PASSTICKET, false),
            createScheme(AuthenticationScheme.ZOWE_JWT, false)
        );

        // no default
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            new AuthenticationSchemeFactory(mock(AuthSourceService.class), schemes);
        });
        assertTrue(exception.getMessage().contains("No scheme"));
    }

    @Test
    void testInit_MultipleDefaults() {
        List<AbstractAuthenticationScheme> schemes = Arrays.asList(
            createScheme(AuthenticationScheme.BYPASS, true),
            createScheme(AuthenticationScheme.HTTP_BASIC_PASSTICKET, true),
            createScheme(AuthenticationScheme.ZOWE_JWT, false)
        );
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            new AuthenticationSchemeFactory(mock(AuthSourceService.class), schemes);
        });
        assertTrue(exception.getMessage().contains("Multiple scheme"));
        assertTrue(exception.getMessage().contains("as default"));
        assertTrue(exception.getMessage().contains(AuthenticationScheme.BYPASS.getScheme()));
        assertTrue(exception.getMessage().contains(AuthenticationScheme.HTTP_BASIC_PASSTICKET.getScheme()));
    }

    @Test
    void testInit_MultipleSameScheme() {
        List<AbstractAuthenticationScheme> schemes = Arrays.asList(
            createScheme(AuthenticationScheme.BYPASS, true),
            createScheme(AuthenticationScheme.BYPASS, false));
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            new AuthenticationSchemeFactory(mock(AuthSourceService.class), schemes);
        });
        assertTrue(exception.getMessage().contains("Multiple beans for scheme"));
        assertTrue(exception.getMessage().contains("AuthenticationSchemeFactoryTest$1"));
    }

    @Test
    void testGetSchema() {
        AuthenticationSchemeFactory asf = new AuthenticationSchemeFactory(
            mock(AuthSourceService.class),
            Arrays.asList(
                createScheme(AuthenticationScheme.BYPASS, true),
                createScheme(AuthenticationScheme.HTTP_BASIC_PASSTICKET, false),
                createScheme(AuthenticationScheme.ZOWE_JWT, false)
            )
        );

        assertEquals(AuthenticationScheme.BYPASS, asf.getSchema(AuthenticationScheme.BYPASS).getScheme());
        assertEquals(AuthenticationScheme.HTTP_BASIC_PASSTICKET, asf.getSchema(AuthenticationScheme.HTTP_BASIC_PASSTICKET).getScheme());
        assertEquals(AuthenticationScheme.ZOWE_JWT, asf.getSchema(AuthenticationScheme.ZOWE_JWT).getScheme());
        // default one
        assertEquals(AuthenticationScheme.BYPASS, asf.getSchema(null).getScheme());
    }

    @Test
    void testGetAuthenticationCommand() {
        final AbstractAuthenticationScheme byPass = spy(createScheme(AuthenticationScheme.BYPASS, true));
        final AbstractAuthenticationScheme passTicket = spy(createScheme(AuthenticationScheme.HTTP_BASIC_PASSTICKET, false));

        AuthSourceService as = mock(AuthSourceService.class);
        AuthenticationSchemeFactory asf = new AuthenticationSchemeFactory(as, Arrays.asList(byPass, passTicket));
        Authentication authentication = new Authentication(AuthenticationScheme.BYPASS, "applid1");

        HttpServletRequest request = mock(HttpServletRequest.class);
        RequestContext requestContext = new RequestContext();
        requestContext.setRequest(request);

        QueryResponse qr = new QueryResponse("domain", "userId", new Date(), new Date(), QueryResponse.Source.ZOWE);
        when(as.getAuthSource()).thenReturn(Optional.of(new JwtAuthSource("jwtToken123")));
        when(as.parse(null)).thenReturn(null);
        when(as.parse(new JwtAuthSource("jwtToken123"))).thenReturn(qr);

        verify(byPass, times(0)).createCommand(eq(authentication), eq(null));
        verify(passTicket, times(0)).createCommand(eq(authentication), eq(null));
        asf.getAuthenticationCommand(authentication);

        verify(byPass, times(1)).createCommand(eq(authentication), eq(new JwtAuthSource("jwtToken123")));
        verify(passTicket, times(0)).createCommand(eq(authentication), eq(new JwtAuthSource("jwtToken123")));
        authentication.setScheme(AuthenticationScheme.HTTP_BASIC_PASSTICKET);
        asf.getAuthenticationCommand(authentication);

        verify(byPass, times(1)).createCommand(eq(authentication), eq(new JwtAuthSource("jwtToken123")));
        verify(passTicket, times(1)).createCommand(eq(authentication), eq(new JwtAuthSource("jwtToken123")));
        authentication.setScheme(null);
        asf.getAuthenticationCommand(authentication);

        verify(byPass, times(2)).createCommand(eq(authentication), eq(new JwtAuthSource("jwtToken123")));
        verify(passTicket, times(1)).createCommand(eq(authentication), eq(new JwtAuthSource("jwtToken123")));
        asf.getAuthenticationCommand(null);

        verify(byPass, times(2)).createCommand(eq(authentication), eq(new JwtAuthSource("jwtToken123")));
        verify(byPass, times(1)).createCommand(eq(null), eq(new JwtAuthSource("jwtToken123")));
        verify(passTicket, times(1)).createCommand(eq(authentication), eq(new JwtAuthSource("jwtToken123")));

        RequestContext.testSetCurrentContext(requestContext);

        verify(byPass, times(0)).createCommand(eq(authentication), eq(null));
        verify(passTicket, times(0)).createCommand(eq(authentication), eq(null));
        authentication.setScheme(AuthenticationScheme.HTTP_BASIC_PASSTICKET);
        asf.getAuthenticationCommand(authentication);
        verify(byPass, times(0)).createCommand(eq(authentication), eq(null));
//        verify(passTicket, times(1)).createCommand(eq(authentication), eq(new JwtAuthSource("jwtToken123")));
    }

    @Test
    void testUnknownScheme() {
        AuthenticationSchemeFactory asf = new AuthenticationSchemeFactory(
            mock(AuthSourceService.class),
            Arrays.asList(
                createScheme(AuthenticationScheme.BYPASS, true),
                createScheme(AuthenticationScheme.HTTP_BASIC_PASSTICKET, false),
                createScheme(AuthenticationScheme.ZOWE_JWT, false)
            )
        );

        assertNotNull(asf.getSchema(AuthenticationScheme.BYPASS));
        assertNotNull(asf.getSchema(AuthenticationScheme.HTTP_BASIC_PASSTICKET));
        assertNotNull(asf.getSchema(AuthenticationScheme.ZOWE_JWT));

        // missing implementation
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> asf.getSchema(AuthenticationScheme.ZOSMF));
        assertTrue(exception.getMessage().contains("Unknown scheme"));

        assertSame(COMMAND, asf.getAuthenticationCommand(new Authentication(AuthenticationScheme.ZOWE_JWT, "applid")));
        assertSame(COMMAND, asf.getAuthenticationCommand(new Authentication(null, "applid")));

        // missing implementation
        Authentication authentication = new Authentication(AuthenticationScheme.ZOSMF, "applid");
        exception = assertThrows(IllegalArgumentException.class, () -> asf.getAuthenticationCommand(authentication));
        assertTrue(exception.getMessage().contains("Unknown scheme"));
    }

}
