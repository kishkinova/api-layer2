/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.gateway.security.login.x509;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.zowe.apiml.gateway.security.login.Providers;
import org.zowe.apiml.gateway.security.login.zosmf.ZosmfAuthenticationProvider;
import org.zowe.apiml.gateway.security.service.AuthenticationService;
import org.zowe.apiml.gateway.utils.X509Utils;
import org.zowe.apiml.passticket.IRRPassTicketGenerationException;
import org.zowe.apiml.passticket.PassTicketService;
import org.zowe.apiml.security.common.error.AuthenticationTokenException;
import org.zowe.apiml.security.common.token.TokenAuthentication;
import org.zowe.apiml.security.common.token.X509AuthenticationToken;

import java.security.cert.X509Certificate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class X509AuthenticationProviderTest {

    private X509AuthenticationMapper x509AuthenticationMapper;
    private AuthenticationService authenticationService;
    private X509AuthenticationProvider x509AuthenticationProvider;

    private PassTicketService passTicketService;
    private ZosmfAuthenticationProvider zosmfAuthenticationProvider;
    private Providers providers;

    private X509Certificate[] x509Certificate = new X509Certificate[]{
        X509Utils.getCertificate(X509Utils.correctBase64("zowe"), "CN=user"),
    };

    @BeforeEach
    void setUp() {
        x509AuthenticationMapper = mock(X509AuthenticationMapper.class);

        authenticationService = mock(AuthenticationService.class);
        passTicketService = mock(PassTicketService.class);
        zosmfAuthenticationProvider = mock(ZosmfAuthenticationProvider.class);
        providers = mock(Providers.class);
        when(authenticationService.createJwtToken("user", "security-domain", null)).thenReturn("jwt");
        when(authenticationService.createTokenAuthentication("user", "jwt")).thenReturn(new TokenAuthentication("user", "jwt"));
        x509AuthenticationProvider = new X509AuthenticationProvider(x509AuthenticationMapper
            , authenticationService, passTicketService, zosmfAuthenticationProvider, providers);
        x509AuthenticationProvider.isClientCertEnabled = true;
    }

    @Test
    void givenZosmfIsntPresent_whenProvidedCertificate_shouldReturnToken() {
        when(providers.isZosmfAvailable()).thenReturn(false);
        when(x509AuthenticationMapper.mapCertificateToMainframeUserId(x509Certificate[0])).thenReturn("user");
        TokenAuthentication token = (TokenAuthentication) x509AuthenticationProvider.authenticate(new X509AuthenticationToken(x509Certificate));
        assertEquals("jwt", token.getCredentials());
    }

    @Test
    void givenZosmfIsntPresentBecauseOfError_whenProvidedCertificate_shouldReturnToken() {
        when(providers.isZosmfAvailable()).thenThrow(new AuthenticationServiceException("zOSMF id invalid"));
        when(x509AuthenticationMapper.mapCertificateToMainframeUserId(x509Certificate[0])).thenReturn("user");
        TokenAuthentication token = (TokenAuthentication) x509AuthenticationProvider.authenticate(new X509AuthenticationToken(x509Certificate));
        assertEquals("jwt", token.getCredentials());
    }

    @Test
    void givenZosmfIsntPresent_whenWrongTokenProvided_ThrowException() {
        when(providers.isZosmfAvailable()).thenReturn(false);
        when(x509AuthenticationMapper.mapCertificateToMainframeUserId(x509Certificate[0])).thenReturn("user");
        TokenAuthentication token = new TokenAuthentication("user", "user");
        AuthenticationTokenException exception = assertThrows(AuthenticationTokenException.class, () -> x509AuthenticationProvider.authenticate(token));
        assertEquals("Wrong authentication token. " + TokenAuthentication.class, exception.getMessage());
    }

    @Test
    void x509AuthenticationIsSupported() {
        assertTrue(x509AuthenticationProvider.supports(X509AuthenticationToken.class));
    }

    @Test
    void givenZosmfIsntPresent_givenZosmfIsntPresent_whenCommonNameIsNotCorrect_returnNull() {
        when(providers.isZosmfAvailable()).thenReturn(false);
        when(x509AuthenticationMapper.mapCertificateToMainframeUserId(x509Certificate[0])).thenReturn("wrong username");
        assertNull(x509AuthenticationProvider.authenticate(new X509AuthenticationToken(x509Certificate)));
    }

    @Test
    void givenX509AuthIsDisabled_whenRequested_thenNullIsReturned() {
        x509AuthenticationProvider.isClientCertEnabled = false;
        assertNull(x509AuthenticationProvider.authenticate(new X509AuthenticationToken(x509Certificate)));
    }

    @Test
    void givenZosmfIsPresent_whenValidCertificateAndPassTicketGenerate_returnZosmfJwtToken() throws IRRPassTicketGenerationException {
        String validUsername = "validUsername";
        String validZosmfApplId = "IZUDFLT";

        when(providers.isZosmfAvailable()).thenReturn(true);
        when(providers.isZosfmUsed()).thenReturn(true);
        when(x509AuthenticationMapper.mapCertificateToMainframeUserId(x509Certificate[0])).thenReturn(validUsername);
        when(passTicketService.generate(validUsername, validZosmfApplId)).thenReturn("validPassticket");
        Authentication authentication = new TokenAuthentication(validUsername, "validJwtToken");
        authentication.setAuthenticated(true);
        when(zosmfAuthenticationProvider.authenticate(any())).thenReturn(authentication);

        Authentication result = x509AuthenticationProvider.authenticate(new X509AuthenticationToken(x509Certificate));
        assertThat(result.isAuthenticated(), is(true));
    }

    @Test
    void givenZosmfIPresent_whenPassTicketGeneratesException_thenThrowAuthenticationException() throws IRRPassTicketGenerationException {
        String validUsername = "validUsername";

        when(providers.isZosmfAvailable()).thenReturn(true);
        when(providers.isZosfmUsed()).thenReturn(true);
        when(x509AuthenticationMapper.mapCertificateToMainframeUserId(x509Certificate[0])).thenReturn(validUsername);
        when(passTicketService.generate(validUsername, null)).thenThrow(new IRRPassTicketGenerationException(8,8,8));

        X509AuthenticationToken token = new X509AuthenticationToken(x509Certificate);
        assertThrows(AuthenticationTokenException.class, () -> x509AuthenticationProvider.authenticate(token));
    }

    @Test
    void givenCertificateIsntMappedToUser_whenAuthenticationIsRequired_thenNullIsReturned() {
        when(x509AuthenticationMapper.mapCertificateToMainframeUserId(x509Certificate[0])).thenReturn(null);
        Authentication result = x509AuthenticationProvider.authenticate(new X509AuthenticationToken(x509Certificate));
        assertThat(result, is(nullValue()));
    }
}
