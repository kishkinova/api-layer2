/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.gateway.security.service.token;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.zowe.apiml.gateway.cache.CachingServiceClientException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OIDCTokenProviderTest {


    private static final String BODY = "{\n" +
    "    \"active\": true,\n" +
    "    \"scope\": \"scope\",\n" +
    "    \"exp\": 1664538493,\n" +
    "    \"iat\": 1664534893,\n" +
    "    \"sub\": \"sub\",\n" +
    "    \"aud\": \"aud\",\n" +
    "    \"iss\": \"iss\",\n" +
    "    \"jti\": \"jti\",\n" +
    "    \"token_type\": \"Bearer\",\n" +
    "    \"client_id\": \"id\"\n" +
    "}";

    private static final String NOT_VALID_BODY = "{\n" +
    "    \"active\": false\n" +
    "}";

    private static final String TOKEN = "token";

    private OIDCTokenProvider oidcTokenProvider;
    @Mock
    private CloseableHttpClient httpClient;
    @Mock
    private CloseableHttpResponse response;

    private StatusLine responseStatusLine;
    private BasicHttpEntity responseEntity;

    private ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setup() throws CachingServiceClientException, IOException {
        responseStatusLine = mock(StatusLine.class);
        responseEntity = new BasicHttpEntity();
        responseEntity.setContent(IOUtils.toInputStream("", StandardCharsets.UTF_8));
        when(responseStatusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK);
        when(response.getStatusLine()).thenReturn(responseStatusLine);
        when(response.getEntity()).thenReturn(responseEntity);
        when(httpClient.execute(any())).thenReturn(response);
        oidcTokenProvider = new OIDCTokenProvider(httpClient, mapper, "https://jwksurl", 1L);
        oidcTokenProvider.introspectUrl = "https://acme.com/introspect";
        oidcTokenProvider.clientId = "client_id";
        oidcTokenProvider.clientSecret = "client_secret";
    }

    @Nested
    class GivenInitializationWithJwks {

        @BeforeEach
        void setup() throws ClientProtocolException, IOException {
            responseEntity.setContent(IOUtils.toInputStream(mapper.writeValueAsString(getJwkKeys()), StandardCharsets.UTF_8));
        }

        @Test
        @SuppressWarnings("unchecked")
        void initialized_thenJwksFullfilled() {
            Map<String, JwkKeys> jwks = (Map<String, JwkKeys>) ReflectionTestUtils.getField(oidcTokenProvider, "jwks");
            ReflectionTestUtils.setField(oidcTokenProvider, "registry", "https://acme.com");
            oidcTokenProvider.afterPropertiesSet();
            assertTrue(jwks.containsKey("https://acme.com"));
            assertEquals(getJwkKeys(), jwks.get("https://acme.com"));
        }

        private JwkKeys getJwkKeys() {
            return new JwkKeys(
                singletonList(new JwkKeys.Key("kty", "alg", "kid", "use", "e", "n"))
            );
        }

    }

    @Nested
    class GivenTokenForValidation {
        @Test
        void tokenIsActive_thenReturnValid() {
            responseEntity.setContent(IOUtils.toInputStream(BODY, StandardCharsets.UTF_8));
            assertTrue(oidcTokenProvider.isValid(TOKEN));
        }

        @Test
        void tokenIsExpired_thenReturnInvalid() {
            responseEntity.setContent(IOUtils.toInputStream(NOT_VALID_BODY, StandardCharsets.UTF_8));
            assertFalse(oidcTokenProvider.isValid(TOKEN));
        }

        @Test
        void whenClientThrowsException_thenReturnInvalid() throws IOException {
            ClientProtocolException exception = new ClientProtocolException("http error");
            when(httpClient.execute(any())).thenThrow(exception);
            assertFalse(oidcTokenProvider.isValid(TOKEN));
        }

        @Test
        void whenResponseIsNotValidJson_thenReturnInvalid() {
            responseEntity.setContent(IOUtils.toInputStream("{notValid}", StandardCharsets.UTF_8));
            assertFalse(oidcTokenProvider.isValid(TOKEN));
        }

        @Test
        void whenResponseStatusIsNotOk_thenReturnInvalid() {
            when(responseStatusLine.getStatusCode()).thenReturn(HttpStatus.SC_UNAUTHORIZED);
            assertFalse(oidcTokenProvider.isValid(TOKEN));
        }

    }

    @Nested
    class GivenEmptyTokenProvided {
        @Test
        void whenTokenIsNull_thenReturnInvalid() {
            assertFalse(oidcTokenProvider.isValid(null));
        }

        @Test
        void whenTokenIsEmpty_thenReturnInvalid() {
            assertFalse(oidcTokenProvider.isValid(""));
        }
    }
    @Nested
    class GivenInvalidConfiguration {

        @ParameterizedTest
        @NullSource
        @EmptySource
        @ValueSource(strings = {"not_an_URL", "https//\\:"})
        void whenInvalidIntrospectUrl_thenReturnInvalid(String url) {
            oidcTokenProvider.introspectUrl = url;
            assertFalse(oidcTokenProvider.isValid(TOKEN));
        }

        @ParameterizedTest
        @NullSource
        @EmptySource
        void whenInvalidClientId_thenReturnInvalid(String id) {
            oidcTokenProvider.clientId = id;
            assertFalse(oidcTokenProvider.isValid(TOKEN));
        }

        @ParameterizedTest
        @NullSource
        @EmptySource
        void whenInvalidClientSecret_thenReturnInvalid(String secret) {
            oidcTokenProvider.clientSecret = secret;
            assertFalse(oidcTokenProvider.isValid(TOKEN));
        }
    }

}
