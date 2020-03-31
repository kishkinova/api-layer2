
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.discovery.DiscoveryClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.zowe.apiml.security.common.config.AuthConfigurationProperties;
import org.zowe.apiml.security.common.error.ServiceNotAccessibleException;
import org.zowe.apiml.security.common.token.TokenNotValidException;

/**
 * This implementation is used for version z/OSMF which don't support authentication endpoint. Instant this endpoint
 * it use info endpoint for authentication and verification. This implementation doesn't support invalidation (logout).
 *
 * Bean could be served via {@link ZosmfServiceFacade}
 */
@Service
@Order(2)
public class ZosmfServiceV1 extends AbstractZosmfService {

    public ZosmfServiceV1(
        AuthConfigurationProperties authConfigurationProperties,
        DiscoveryClient discovery,
        @Qualifier("restTemplateWithoutKeystore") RestTemplate restTemplateWithoutKeystore,
        ObjectMapper securityObjectMapper
    ) {
        super(authConfigurationProperties, discovery, restTemplateWithoutKeystore, securityObjectMapper);
    }

    @Override
    public AuthenticationResponse authenticate(Authentication authentication) {
        final String url = getURI(getZosmfServiceId()) + ZOSMF_INFO_END_POINT;

        final HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, getAuthenticationValue(authentication));
        headers.add(ZOSMF_CSRF_HEADER, "");

        try {
            final ResponseEntity<String> responseEntity = restTemplateWithoutKeystore.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                String.class);
            return getAuthenticationResponse(responseEntity);
        } catch (RuntimeException re) {
            throw handleExceptionOnCall(url, re);
        }
    }

    @Override
    public void validate(TokenType type, String token) {
        final String url = getURI(getZosmfServiceId()) + ZOSMF_INFO_END_POINT;

        final HttpHeaders headers = new HttpHeaders();
        headers.add(ZOSMF_CSRF_HEADER, "");
        headers.add(HttpHeaders.COOKIE, type.getCookieName() + "=" + token);

        try {
            ResponseEntity<String> response = restTemplateWithoutKeystore.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                String.class);

            if (response.getStatusCode().is2xxSuccessful()) return;
            if (response.getStatusCodeValue() == 401) {
                throw new TokenNotValidException("Token is not valid.");
            }
            apimlLog.log("org.zowe.apiml.security.serviceUnavailable", url, response.getStatusCodeValue());
            throw new ServiceNotAccessibleException("Could not get an access to z/OSMF service.");
        } catch (RuntimeException re) {
            throw handleExceptionOnCall(url, re);
        }
    }

    @Override
    public void invalidate(TokenType type, String token) {
        // not supported by this version of z/OSMF
    }

    @Override
    public boolean isSupported(int version) {
        return true;
    }

}
