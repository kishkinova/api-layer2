/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.gateway.security.service.zosmf;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.discovery.DiscoveryClient;
import com.nimbusds.jose.jwk.JWKSet;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.zowe.apiml.security.common.config.AuthConfigurationProperties;
import org.zowe.apiml.security.common.error.ServiceNotAccessibleException;
import org.zowe.apiml.security.common.token.TokenNotValidException;
import springfox.documentation.annotations.Cacheable;

import java.text.ParseException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Primary
@Service
@Slf4j
public class ZosmfService extends AbstractZosmfService {

    /**
     * Enumeration of supported security tokens
     */
    @AllArgsConstructor
    @Getter
    public enum TokenType {

        JWT("jwtToken"),
        LTPA("LtpaToken2");

        private final String cookieName;

    }

    /**
     * Response of authentication, contains all data to next processing
     */
    @Data
    @AllArgsConstructor
    @RequiredArgsConstructor
    public static class AuthenticationResponse {
        private String domain;
        private final Map<TokenType, String> tokens;
    }

    /**
     * DTO with base information about z/OSMF (version and realm/domain)
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ZosmfInfo {

        @JsonProperty("zosmf_version")
        private int version;

        @JsonProperty("zosmf_full_version")
        private String fullVersion;

        @JsonProperty(ZOSMF_DOMAIN)
        private String safRealm;

    }

    private static final String PUBLIC_JWK_ENDPOINT = "/jwt/ibm/api/zOSMFBuilder/jwk";

    public ZosmfService(
        final AuthConfigurationProperties authConfigurationProperties,
        final DiscoveryClient discovery,
        final @Qualifier("restTemplateWithoutKeystore") RestTemplate restTemplateWithoutKeystore,
        final ObjectMapper securityObjectMapper
    ) {
        super(
            authConfigurationProperties,
            discovery,
            restTemplateWithoutKeystore,
            securityObjectMapper
        );
    }

    public AuthenticationResponse authenticate(Authentication authentication) {
        AuthenticationResponse authenticationResponse;
        if (authenticationEndpointExists(HttpMethod.POST)) {
            authenticationResponse = issueAuthenticationRequest(
                authentication,
                getURI(getZosmfServiceId()) + ZOSMF_AUTHENTICATE_END_POINT,
                HttpMethod.POST);
        } else {
            String zosmfInfoURIEndpoint = getURI(getZosmfServiceId()) + ZOSMF_INFO_END_POINT;
            authenticationResponse = issueAuthenticationRequest(
                authentication,
                zosmfInfoURIEndpoint,
                HttpMethod.GET);
            authenticationResponse.setDomain(getZosmfRealm(zosmfInfoURIEndpoint));
        }
        return authenticationResponse;
    }

    /**
     *
     * @return String containing the zosmf realm/domain
     */
    @Cacheable("zosmfInfo")
    public String getZosmfRealm(String infoURIEndpoint) {
        final HttpHeaders headers = new HttpHeaders();
        headers.add(ZOSMF_CSRF_HEADER, "");

        try {
            final ResponseEntity<ZosmfInfo> info = restTemplateWithoutKeystore.exchange(
                infoURIEndpoint,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                ZosmfInfo.class
            );

            ZosmfInfo zosmfInfo = info.getBody();

            if (zosmfInfo == null || StringUtils.isEmpty(zosmfInfo.getSafRealm())) {
                apimlLog.log("apiml.security.zosmfDomainIsEmpty", ZOSMF_DOMAIN);
                throw new AuthenticationServiceException("z/OSMF domain cannot be read.");
            }

            return zosmfInfo.getSafRealm();
        } catch (RuntimeException re) {
            throw handleExceptionOnCall(infoURIEndpoint, re);
        }
    }

    /**
     * POST to provided url and return authentication response
     * @param authentication
     * @param url String containing auth endpoint to be used
     * @return AuthenticationResponse containing auth token, either LTPA or JWT
     */
    protected AuthenticationResponse issueAuthenticationRequest(Authentication authentication, String url, HttpMethod httpMethod) {
        final HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, getAuthenticationValue(authentication));
        headers.add(ZOSMF_CSRF_HEADER, "");

        try {
            final ResponseEntity<String> response = restTemplateWithoutKeystore.exchange(
                url,
                httpMethod,
                new HttpEntity<>(null, headers), String.class);
            return getAuthenticationResponse(response);
        } catch (RuntimeException re) {
            throw handleExceptionOnCall(url, re);
        }
    }

    /**
     * Check if call to ZOSMF_AUTHENTICATE_END_POINT resolves
     * @param httpMethod HttpMethod to be checked for existence
     * @return boolean, containing true if endpoint resolves
     */
    @Cacheable("zosmfAuthenticationEndpoint")
    public boolean authenticationEndpointExists(HttpMethod httpMethod) {
        String url = getURI(getZosmfServiceId()) + ZOSMF_AUTHENTICATE_END_POINT;

        final HttpHeaders headers = new HttpHeaders();
        headers.add(ZOSMF_CSRF_HEADER, "");

        try {
            restTemplateWithoutKeystore.exchange(url, httpMethod, new HttpEntity<>(null, headers), String.class);
        } catch (HttpClientErrorException hce) {
            if (hce.getStatusCode().equals(HttpStatus.UNAUTHORIZED)) {
                return true;
            }
            else if (hce.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
                log.warn("The check of z/OSMF JWT authentication endpoint has failed, ensure APAR PH12143 " +
                    "(https://www.ibm.com/support/pages/apar/PH12143) fix has been applied. " +
                    "Using z/OSMF info endpoint as backup.");
                return false;
            }
            log.warn("The check of z/OSMF JWT authentication endpoint has failed with exception", hce);
        } catch (RuntimeException re) {
            log.warn("The check of z/OSMF JWT authentication endpoint has failed with exception", re);
        }
        return false;
    }

    public void validate(TokenType type, String token) {
        if (authenticationEndpointExists(HttpMethod.POST)) {
            final String url = getURI(getZosmfServiceId()) + ZOSMF_AUTHENTICATE_END_POINT;

            final HttpHeaders headers = new HttpHeaders();
            headers.add(ZOSMF_CSRF_HEADER, "");
            headers.add(HttpHeaders.COOKIE, type.getCookieName() + "=" + token);

            try {
                ResponseEntity<String> re = restTemplateWithoutKeystore.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(null, headers), String.class);

                if (re.getStatusCode().is2xxSuccessful())
                    return;
                if (re.getStatusCodeValue() == 401) {
                    throw new TokenNotValidException("Token is not valid.");
                }
                apimlLog.log("org.zowe.apiml.security.serviceUnavailable", url, re.getStatusCodeValue());
                throw new ServiceNotAccessibleException("Could not get an access to z/OSMF service.");
            } catch (RuntimeException re) {
                throw handleExceptionOnCall(url, re);
            }
        }
        log.warn("The request to validate an auth token was unsuccessful, z/OSMF validate endpoint not available");
    }

    public void invalidate(TokenType type, String token) {
        if (authenticationEndpointExists(HttpMethod.DELETE)) {
            final String url = getURI(getZosmfServiceId()) + ZOSMF_AUTHENTICATE_END_POINT;

            final HttpHeaders headers = new HttpHeaders();
            headers.add(ZOSMF_CSRF_HEADER, "");
            headers.add(HttpHeaders.COOKIE, type.getCookieName() + "=" + token);

            try {
                ResponseEntity<String> re = restTemplateWithoutKeystore.exchange(url, HttpMethod.DELETE,
                    new HttpEntity<>(null, headers), String.class);

                if (re.getStatusCode().is2xxSuccessful())
                    return;
                apimlLog.log("org.zowe.apiml.security.serviceUnavailable", url, re.getStatusCodeValue());
                throw new ServiceNotAccessibleException("Could not get an access to z/OSMF service.");
            } catch (RuntimeException re) {
                throw handleExceptionOnCall(url, re);
            }
        }
        log.warn("The request to invalidate an auth token was unsuccessful, z/OSMF invalidate endpoint not available");
    }

    /**
     * Method reads authentication values from answer of REST call. It read all supported tokens, which are returned
     * from z/OSMF.
     *
     * @param responseEntity answer of REST call
     * @return AuthenticationResponse with all supported tokens from responseEntity
     */
    protected ZosmfService.AuthenticationResponse getAuthenticationResponse(ResponseEntity<String> responseEntity) {
        final List<String> cookies = responseEntity.getHeaders().get(HttpHeaders.SET_COOKIE);
        final EnumMap<TokenType, String> tokens = new EnumMap<>(ZosmfService.TokenType.class);
        if (cookies != null) {
            for (final ZosmfService.TokenType tokenType : ZosmfService.TokenType.values()) {
                final String token = readTokenFromCookie(cookies, tokenType.getCookieName());
                if (token != null) tokens.put(tokenType, token);
            }
        }
        return new ZosmfService.AuthenticationResponse(tokens);
    }

    public JWKSet getPublicKeys() {
        final String url = getURI(getZosmfServiceId()) + PUBLIC_JWK_ENDPOINT;

        try {
            final String json = restTemplateWithoutKeystore.getForObject(url, String.class);
            return JWKSet.parse(json);
        } catch (ParseException pe) {
            log.debug("Invalid format of public keys from z/OSMF", pe);
        } catch (HttpClientErrorException.NotFound nf) {
            log.debug("Cannot get public keys from z/OSMF", nf);
        }
        return new JWKSet();
    }
}
