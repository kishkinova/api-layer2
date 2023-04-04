/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.gateway.security.service;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.shared.Application;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.apache.commons.lang.time.DateUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.zowe.apiml.config.service.security.MockedAuthenticationServiceContext;
import org.zowe.apiml.constants.ApimlConstants;
import org.zowe.apiml.gateway.config.CacheConfig;
import org.zowe.apiml.gateway.security.service.zosmf.ZosmfService;
import org.zowe.apiml.product.constants.CoreService;
import org.zowe.apiml.security.SecurityUtils;
import org.zowe.apiml.security.common.config.AuthConfigurationProperties;
import org.zowe.apiml.security.common.token.QueryResponse;
import org.zowe.apiml.security.common.token.TokenAuthentication;
import org.zowe.apiml.security.common.token.TokenExpireException;
import org.zowe.apiml.security.common.token.TokenNotValidException;
import org.zowe.apiml.util.CacheUtils;
import org.zowe.apiml.util.EurekaUtils;

import javax.servlet.http.Cookie;
import java.security.Key;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthenticationServiceTest { //NOSONAR, needs to be public

    public static final String ZOSMF = "zosmf";

    private static final String USER = "Me";
    private Set<String> scopes;
    private static final String DOMAIN = "this.com";
    private static final String LTPA = "ltpaToken";
    private static final SignatureAlgorithm ALGORITHM = SignatureAlgorithm.RS256;

    private static Key privateKey;
    private static PublicKey publicKey;

    @Mock
    private ApplicationContext applicationContext;

    private AuthenticationService authService;

    private AuthConfigurationProperties authConfigurationProperties;

    @Mock
    private JwtSecurity jwtSecurityInitializer;
    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ZosmfService zosmfService;
    @Mock
    private DiscoveryClient discoveryClient;
    @Mock
    private CacheUtils cacheUtils;
    @Mock
    private CacheManager cacheManager;


    static {
        KeyPair keyPair = SecurityUtils.generateKeyPair("RSA", 2048);
        if (keyPair != null) {
            privateKey = keyPair.getPrivate();
            publicKey = keyPair.getPublic();
        }
    }

    @BeforeEach
    void setup() {

        authConfigurationProperties = new AuthConfigurationProperties();
        authConfigurationProperties.getZosmf().setServiceId(ZOSMF);

        authService = new AuthenticationService(
            applicationContext, authConfigurationProperties, jwtSecurityInitializer,
            zosmfService, discoveryClient, restTemplate, cacheManager, cacheUtils
        );
        scopes = new HashSet<>();
        scopes.add("Service1");
        scopes.add("Service2");
        ReflectionTestUtils.setField(authService, "meAsProxy", authService);
    }

    @Nested
    class GivenCorrectInputsTest {

        @BeforeEach
        void setup() {
            stubJWTSecurityForSign();
        }

        @Test
        void thenCreatePersonalAccessToken() {
            String pat = authService.createLongLivedJwtToken(USER, 60, scopes);
            QueryResponse parsedPAT = authService.parseJwtWithSignature(pat);
            assertEquals(QueryResponse.Source.ZOWE_PAT, parsedPAT.getSource());
        }

        @Test
        void thenCreateValidJwtToken() {
            when(jwtSecurityInitializer.getJwtPublicKey()).thenReturn(publicKey);
            String jwtToken = authService.createJwtToken(USER, DOMAIN, LTPA);

            TokenAuthentication token = new TokenAuthentication(jwtToken);
            TokenAuthentication jwtValidation = authService.validateJwtToken(token);

            assertEquals(USER, jwtValidation.getPrincipal());
            assertEquals(jwtValidation.getCredentials(), jwtToken);
            assertTrue(jwtValidation.isAuthenticated());
        }

        @Test
        void thenParseJwtTokenAsQueryResponse() {
            String jwtToken = authService.createJwtToken(USER, DOMAIN, LTPA);

            String dateNow = new Date().toString().substring(0, 16);
            QueryResponse parsedToken = authService.parseJwtToken(jwtToken);

            assertEquals(QueryResponse.class, parsedToken.getClass());
            assertEquals(DOMAIN, parsedToken.getDomain());
            assertEquals(USER, parsedToken.getUserId());
            assertEquals(parsedToken.getCreation().toString().substring(0, 16), dateNow);
            Date toBeExpired = DateUtils.addHours(parsedToken.getCreation(), 8);
            assertEquals(parsedToken.getExpiration(), toBeExpired);
        }

        @Test
        void thenGetTokenWithDefaultExpiration() {
            String jwt1 = authService.createJwtToken("user", DOMAIN, LTPA);

            QueryResponse qr1 = authService.parseJwtToken(jwt1);
            Date toBeExpired = DateUtils.addSeconds(qr1.getCreation(), authConfigurationProperties.getTokenProperties().getExpirationInSeconds());
            assertEquals(qr1.getExpiration(), toBeExpired);
        }

        @Test
        void thenGetShortLivedToken() {
            String jwt2 = authService.createJwtToken("expire", DOMAIN, LTPA);
            QueryResponse qr2 = authService.parseJwtToken(jwt2);
            Date toBeExpired2 = DateUtils.addSeconds(qr2.getCreation(), (int) authConfigurationProperties.getTokenProperties().getShortTtlExpirationInSeconds());
            assertEquals(qr2.getExpiration(), toBeExpired2);
        }

    }

    @Nested
    class GivenInvalidTokenAuthenticationTest {

        @Test
        void thenThrowTokenNotValidException() {
            stubJWTSecurityForSignAndVerify();
            String jwtToken = authService.createJwtToken(USER, DOMAIN, LTPA);
            String brokenToken = jwtToken + "not";
            TokenAuthentication token = new TokenAuthentication(brokenToken);
            assertThrows(
                TokenNotValidException.class,
                () -> authService.validateJwtToken(token)
            );
        }

        @Test
        void givenNullValue_thenThrowTokenNotValidException() {
            assertThrows(
                TokenNotValidException.class,
                () -> authService.validateJwtToken((TokenAuthentication) null)
            );
        }

        @Test
        void givenExpiredToken_thenThrowsTokenExpireException() {
            TokenAuthentication token = new TokenAuthentication(createExpiredJwtToken(privateKey));
            assertThrows(
                TokenExpireException.class,
                () -> authService.validateJwtToken(token)
            );
        }
    }

    @Nested
    class GivenInvalidTokenStringTest {
        @Test
        void thenThrowsTokenNotValidException() {
            assertThrows(
                TokenNotValidException.class,
                () -> authService.validateJwtToken((String) null)
            );
        }

    }

    @Nested
    class GivenReadJWTFromRequestTest {

        @Test
        void givenJwtInCookie_thenReadJwtTokenFromRequestCookie() {
            String jwtToken = "token";
            MockHttpServletRequest request = new MockHttpServletRequest();

            Optional<String> optionalToken = authService.getJwtTokenFromRequest(request);
            assertFalse(optionalToken.isPresent());

            request.setCookies(new Cookie("apimlAuthenticationToken", jwtToken));

            optionalToken = authService.getJwtTokenFromRequest(request);
            assertTrue(optionalToken.isPresent());
            assertEquals(optionalToken.get(), jwtToken);
        }

        @Test
        void givenJwtInAuthorizationHeader_thenReadJwtFromRequestHeader() {
            String jwtToken = "token";
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer ");
            Optional<String> optionalToken = authService.getJwtTokenFromRequest(request);
            assertFalse(optionalToken.isPresent());

            request = new MockHttpServletRequest();
            request.addHeader("Authorization", String.format("Bearer %s", jwtToken));
            optionalToken = authService.getJwtTokenFromRequest(request);
            assertTrue(optionalToken.isPresent());
            assertEquals(optionalToken.get(), jwtToken);
        }

        @Nested
        class GivenPriorityOfTokensTest {

            MockHttpServletRequest request;

            @BeforeEach
            void setup() {
                request = new MockHttpServletRequest();
                request.addHeader(HttpHeaders.AUTHORIZATION, ApimlConstants.BEARER_AUTHENTICATION_PREFIX + " jwtInAuthHeader");
            }

            @Test
            void givenJwtInCookieAndHeader_whenGetJwtTokenFromRequest_thenPreferCookie() {
                String cookieName = authConfigurationProperties.getCookieProperties().getCookieName();
                request.setCookies(new Cookie(cookieName, "jwtInCookies"));

                Optional<String> token = authService.getJwtTokenFromRequest(request);
                assertTrue(token.isPresent());
                assertEquals("jwtInCookies", token.get());
            }

            @Test
            void givenOtherCookiesAndJwtInHeader_whenGetJwtTokenFromRequest_thenTakeFromHeader() {
                request.setCookies(new Cookie("cookie", "value"));

                Optional<String> token = authService.getJwtTokenFromRequest(request);
                assertTrue(token.isPresent());
                assertEquals("jwtInAuthHeader", token.get());
            }

        }

    }

    @Nested
    class GivenPATInTheRequestTest {
        @Test
        void givenTokenIsAvailableInCookie_thenGetFromCookie() {
            String pat = "personalAccessToken";
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setCookies(new Cookie("personalAccessToken", pat));
            Optional<String> result = authService.getPATFromRequest(request);
            assertTrue(result.isPresent());
            assertEquals(pat, result.get());
        }

        @Test
        void givenTokenNotPresentInCookie_thenGetFromHeader() {
            String pat = "personalAccessToken";
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader(ApimlConstants.PAT_HEADER_NAME, pat);
            Optional<String> result = authService.getPATFromRequest(request);
            assertTrue(result.isPresent());
            assertEquals(pat, result.get());
        }

        @Test
        void givenNoTokenInRequest_thenReturnEmptyResult() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            Optional<String> result = authService.getPATFromRequest(request);
            assertFalse(result.isPresent());
        }
    }

    @Nested
    class GivenReadLTPATokenTest {

        @Test
        void givenLTPAExists_thenReadLtpaTokenFromJwtToken() {
            stubJWTSecurityForSignAndVerify();
            String jwtToken = authService.createJwtToken(USER, DOMAIN, LTPA);
            assertEquals(LTPA, authService.getLtpaTokenWithValidation(jwtToken));
        }

        @Test
        void givenInvalidJWT_thenThrowTokenNotValidException() {
            stubJWTSecurityForSign();
            String jwtToken = authService.createJwtToken(USER, DOMAIN, LTPA);
            String brokenToken = jwtToken + "not";
            assertThrows(
                TokenNotValidException.class,
                () -> authService.getLtpaTokenWithValidation(brokenToken)
            );
        }

        @Test
        void givenExpiredJWT_thenThrowTokenExpireException() {
            String expiredJwtToken = createExpiredJwtToken(privateKey);
            when(jwtSecurityInitializer.getJwtPublicKey()).thenReturn(publicKey);
            assertThrows(
                TokenExpireException.class,
                () -> authService.getLtpaTokenWithValidation(expiredJwtToken)
            );
        }

        @Test
        void givenIncorrectLTPAToken_thenThrowTokenNotValidException() {
            for (String jwtToken : new String[]{"header.body.sign", "wrongJwtToken", ""}) {
                Throwable t = assertThrows(TokenNotValidException.class, () -> authService.getLtpaToken(jwtToken));
                assertTrue(t.getMessage().contains("Token is not valid."));
            }
        }

    }

    private String createExpiredJwtToken(Key secretKey) {
        return createJwtTokenWithExpiry(secretKey, System.currentTimeMillis() - 1000);
    }

    private String createJwtTokenWithExpiry(Key secretKey, long expireAt) {
        return Jwts.builder()
            .setExpiration(new Date(expireAt))
            .setIssuer(authConfigurationProperties.getTokenProperties().getIssuer())
            .signWith(ALGORITHM, secretKey)
            .compact();
    }

    private InstanceInfo createInstanceInfo(String instanceId, String hostName, int securePort, boolean isSecureEnabled) {
        InstanceInfo out = mock(InstanceInfo.class);
        when(out.getInstanceId()).thenReturn(instanceId);
        when(out.getHostName()).thenReturn(hostName);
        when(out.getSecurePort()).thenReturn(securePort);
        when(out.isPortEnabled(InstanceInfo.PortType.SECURE)).thenReturn(isSecureEnabled);
        return out;
    }

    @Nested
    class GivenInvalidateZosmfTokenTest {

        public static final String JWT_TOKEN = "zosmfJwtToken";
        public static final String LTPA_TOKEN = "zosmfLtpaToken";

        @Test
        void givenNoInstancesAvailable_thenReturnFalse() {

            when(discoveryClient.getApplication(CoreService.GATEWAY.getServiceId())).thenReturn(null);
            assertFalse(authService.invalidateJwtToken(JWT_TOKEN, true));

        }

        @Test
        void givenTokenWasNotInvalidateOnAnotherInstance_thenRethrowException() {

            stubJWTSecurityForSign();
            authConfigurationProperties.getTokenProperties().setIssuer(ZOSMF);
            String token = authService.createJwtToken("user", "dom", null);
            Mockito.doThrow(new BadCredentialsException("Invalid Credentials")).when(zosmfService).invalidate(ZosmfService.TokenType.JWT, token);

            Exception exception = assertThrows(BadCredentialsException.class, () -> {
                authService.invalidateJwtToken(token, false);
            });

            assertEquals("Invalid Credentials", exception.getMessage());
            verify(zosmfService, times(1)).invalidate(ZosmfService.TokenType.JWT, token);
        }

        @Test
        void givenTokenWasAlreadyInvalidateOnAnotherInstance_thenReturnInvalidatedTrue() {
            Application application = mock(Application.class);
            ApplicationInfoManager applicationInfoManager = mock(ApplicationInfoManager.class);
            InstanceInfo instanceInfo = mock(InstanceInfo.class);
            InstanceInfo instanceInfo2 = mock(InstanceInfo.class);
            when(discoveryClient.getApplication(CoreService.GATEWAY.getServiceId())).thenReturn(application);
            when(discoveryClient.getApplicationInfoManager()).thenReturn(applicationInfoManager);
            when(applicationInfoManager.getInfo()).thenReturn(instanceInfo);
            when(instanceInfo.getInstanceId()).thenReturn("instanceId");
            when(application.getInstances()).thenReturn(Collections.singletonList(instanceInfo2));
            when(instanceInfo2.getInstanceId()).thenReturn("insncId2");
            when(instanceInfo2.getSecurePort()).thenReturn(100);
            when(instanceInfo2.getHostName()).thenReturn("localhost");

            stubJWTSecurityForSign();
            authConfigurationProperties.getTokenProperties().setIssuer(ZOSMF);
            String token = authService.createJwtToken("user", DOMAIN, null);
            doNothing().when(restTemplate).delete("http://localhost:0/gateway/auth/invalidate/" + token);
            Mockito.doThrow(new BadCredentialsException("Invalid Credentials")).when(zosmfService).invalidate(ZosmfService.TokenType.JWT, token);

            assertTrue(authService.invalidateJwtToken(token, true));
        }

        @Test
        void invalidateZosmfJwtToken() {
            stubJWTSecurityForSign();
            authConfigurationProperties.getTokenProperties().setIssuer(ZOSMF);
            String token = authService.createJwtToken("user", DOMAIN, null);

            assertTrue(authService.invalidateJwtToken(token, false));
            verify(zosmfService, times(1)).invalidate(ZosmfService.TokenType.JWT, token);
        }

        @Test
        void invalidateZosmfLtpaToken() {

            stubJWTSecurityForSign();
            String token = authService.createJwtToken("user", DOMAIN, LTPA_TOKEN);

            assertTrue(authService.invalidateJwtToken(token, false));
            verify(zosmfService, times(1)).invalidate(ZosmfService.TokenType.LTPA, LTPA_TOKEN);
        }


        @Test
        void thenValidateZosmfJwtToken() {
            final String userId = "userIdSource";
            stubJWTSecurityForSign();
            authConfigurationProperties.getTokenProperties().setIssuer(ZOSMF);
            String zosmfJwt = authService.createJwtToken(userId, DOMAIN, LTPA_TOKEN);

            when(zosmfService.validate(zosmfJwt)).thenReturn(true);
            TokenAuthentication tokenAuthentication = authService.validateJwtToken(zosmfJwt);
            assertTrue(tokenAuthentication.isAuthenticated());
            assertEquals(zosmfJwt, tokenAuthentication.getCredentials());
            assertEquals(userId, tokenAuthentication.getPrincipal());
            verify(zosmfService, times(1)).validate(zosmfJwt);
        }

    }

    void stubJWTSecurityForSignAndVerify() {
        stubJWTSecurityForSign();
        when(jwtSecurityInitializer.getJwtPublicKey()).thenReturn(publicKey);
    }

    void stubJWTSecurityForSign() {
        when(jwtSecurityInitializer.getSignatureAlgorithm()).thenReturn(ALGORITHM);
        when(jwtSecurityInitializer.getJwtSecret()).thenReturn(privateKey);
    }


    @Nested
    @ExtendWith(SpringExtension.class)
    @ContextConfiguration(classes = {
        CacheConfig.class,
        MockedAuthenticationServiceContext.class
    })
    class GivenCacheJWTTest {
        @Autowired
        private AuthenticationService authService;

        @Autowired
        private JwtSecurity jwtSecurityInitializer;

        @Test
        void thenUseCache() {
            when(jwtSecurityInitializer.getSignatureAlgorithm()).thenReturn(ALGORITHM);
            when(jwtSecurityInitializer.getJwtSecret()).thenReturn(privateKey);
            when(jwtSecurityInitializer.getJwtPublicKey()).thenReturn(publicKey);
            String jwtToken01 = authService.createJwtToken("user01", "domain01", "ltpa01");
            String jwtToken02 = authService.createJwtToken("user02", "domain02", "ltpa02");

            assertFalse(authService.isInvalidated(jwtToken01));
            assertFalse(authService.isInvalidated(jwtToken02));

            verify(jwtSecurityInitializer, never()).getJwtPublicKey();

            assertTrue(authService.validateJwtToken(jwtToken01).isAuthenticated());
            verify(jwtSecurityInitializer, times(1)).getJwtPublicKey();
            assertTrue(authService.validateJwtToken(jwtToken01).isAuthenticated());
            verify(jwtSecurityInitializer, times(1)).getJwtPublicKey();

            assertTrue(authService.validateJwtToken(jwtToken02).isAuthenticated());
            verify(jwtSecurityInitializer, times(2)).getJwtPublicKey();

            authService.invalidateJwtToken(jwtToken01, false);
            assertTrue(authService.validateJwtToken(jwtToken02).isAuthenticated());
            verify(jwtSecurityInitializer, times(2)).getJwtPublicKey();
            assertFalse(authService.validateJwtToken(jwtToken01).isAuthenticated());
            verify(jwtSecurityInitializer, times(3)).getJwtPublicKey();
        }

    }

    @Test
    void givenCreateTokenAuthentication_thenCreateCorrectObject() {
        Consumer<TokenAuthentication> assertTokenAuthentication = x -> {
            assertNotNull(x);
            assertTrue(x.isAuthenticated());
            assertEquals("userXYZ", x.getPrincipal());
            assertEquals("jwtTokenXYZ", x.getCredentials());
        };

        TokenAuthentication tokenAuthentication;

        tokenAuthentication = authService.createTokenAuthentication("userXYZ", "jwtTokenXYZ");
        assertTokenAuthentication.accept(tokenAuthentication);
    }


    @Nested
    class GivenDistributedInvalidationTest {

        @Test
        void whenNoServiceAvailable_thenReturnFailure() {
            when(discoveryClient.getApplication("gateway")).thenReturn(null);
            assertFalse(authService.distributeInvalidate("instanceId"));
        }

        @Test
        void whenNoInstanceAvailable_thenReturnFailure() {
            Application application = mock(Application.class);
            when(application.getByInstanceId("instanceId")).thenReturn(null);

            when(discoveryClient.getApplication("gateway")).thenReturn(application);
            assertFalse(authService.distributeInvalidate("instanceId"));
        }

        @Test
        void whenInstancesAvailable_thenReturnSuccess() {

            InstanceInfo instanceInfo = createInstanceInfo("instanceId", "host", 1433, true);

            Application application = mock(Application.class);
            when(application.getByInstanceId("instanceId")).thenReturn(instanceInfo);
            when(discoveryClient.getApplication("gateway")).thenReturn(application);

            List<Object> elementsInCache = new ArrayList<>();
            elementsInCache.add("a");
            elementsInCache.add("b");
            when(cacheUtils.getAllRecords(any(), any())).thenReturn(elementsInCache);

            authService.distributeInvalidate(instanceInfo.getInstanceId());

            verify(restTemplate, times(1)).delete(EurekaUtils.getUrl(instanceInfo) + "/gateway/auth/invalidate/{}", "a");
            verify(restTemplate, times(1)).delete(EurekaUtils.getUrl(instanceInfo) + "/gateway/auth/invalidate/{}", "b");
        }

    }

    @Nested
    class GivenOIDCTokenInTheRequestTest {

        @Test
        void givenTokenIsPresent_thenGetFromHeader() {
            String token = "oidcToken";
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader(ApimlConstants.OIDC_HEADER_NAME, token);
            Optional<String> result = authService.getOIDCTokenFromRequest(request);
            assertTrue(result.isPresent());
            assertEquals(token, result.get());
        }

        @Test
        void givenNoTokenInRequest_thenReturnEmptyResult() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            Optional<String> result = authService.getOIDCTokenFromRequest(request);
            assertFalse(result.isPresent());
        }

    }

}
