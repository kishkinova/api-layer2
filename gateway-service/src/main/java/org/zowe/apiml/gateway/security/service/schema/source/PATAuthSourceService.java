/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.gateway.security.service.schema.source;

import com.netflix.zuul.context.RequestContext;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.zowe.apiml.gateway.security.service.AuthenticationService;
import org.zowe.apiml.gateway.security.service.TokenCreationService;
import org.zowe.apiml.message.log.ApimlLogger;
import org.zowe.apiml.product.logging.annotations.InjectApimlLogger;
import org.zowe.apiml.security.common.token.AccessTokenProvider;
import org.zowe.apiml.security.common.token.QueryResponse;

import java.util.function.Function;
import java.util.function.Predicate;

import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.SERVICE_ID_KEY;

@RequiredArgsConstructor
@Slf4j
@Service
public class PATAuthSourceService extends TokenAuthSourceService {

    @InjectApimlLogger
    protected final ApimlLogger logger = ApimlLogger.empty();

    private final AuthenticationService authenticationService;
    private final AccessTokenProvider tokenProvider;
    private final TokenCreationService tokenService;

    @Override
    protected ApimlLogger getLogger() {
        return logger;
    }

    @Override
    public AuthenticationService getAuthenticationService() {
        return authenticationService;
    }

    @Override
    public Predicate<QueryResponse.Source> getPredicate() {
        return (source) -> !QueryResponse.Source.ZOWE_PAT.equals(source);
    }

    @Override
    public Function<String, AuthSource> getMapper() {
        return PATAuthSource::new;
    }

    @Override
    public boolean isValid(AuthSource authSource) {
        String token = (String) authSource.getRawSource();
        RequestContext context = RequestContext.getCurrentContext();
        String serviceId = (String) context.get(SERVICE_ID_KEY);
        boolean validForScopes = tokenProvider.isValidForScopes(token, serviceId);
        boolean invalidate = tokenProvider.isInvalidated(token);
        try {
            return validForScopes && !invalidate;
        } catch (SignatureException e) {
            return false;
        }
    }

    @Override
    public AuthSource.Parsed parse(AuthSource authSource) {
        if (authSource instanceof PATAuthSource) {
            String jwt = (String) authSource.getRawSource();
            QueryResponse response = authenticationService.parseJwtWithSignature(jwt);

            AuthSource.Origin origin = AuthSource.Origin.valueByIssuer(response.getSource().name());
            return new PATAuthSource.Parsed(response.getUserId(), response.getCreation(), response.getExpiration(), origin);
        }
        return null;
    }

    @Override
    public String getLtpaToken(AuthSource authSource) {
        return getJWT(authSource);
    }

    @Override
    public String getJWT(AuthSource authSource) {
        PATAuthSource.Parsed parsed = (PATAuthSource.Parsed) parse(authSource);
        String zosmfToken = tokenService.createJwtTokenWithoutCredentials(parsed.getUserId());
        QueryResponse response = authenticationService.parseJwtToken(zosmfToken);
        AuthSource.Origin origin = AuthSource.Origin.valueByIssuer(response.getSource().name());
        if (AuthSource.Origin.ZOWE.equals(origin)) {
            zosmfToken = authenticationService.getLtpaToken(zosmfToken);
        }
        return zosmfToken;
    }
}
