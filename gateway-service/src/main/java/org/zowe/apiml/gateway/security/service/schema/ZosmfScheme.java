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

import com.netflix.appinfo.InstanceInfo;
import com.netflix.zuul.context.RequestContext;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.zowe.apiml.auth.Authentication;
import org.zowe.apiml.auth.AuthenticationScheme;
import org.zowe.apiml.gateway.security.login.LoginProvider;
import org.zowe.apiml.gateway.security.service.schema.source.AuthSchemeException;
import org.zowe.apiml.gateway.security.service.schema.source.AuthSource;
import org.zowe.apiml.gateway.security.service.schema.source.AuthSourceService;
import org.zowe.apiml.gateway.security.service.schema.source.JwtAuthSource;
import org.zowe.apiml.gateway.security.service.zosmf.ZosmfService;
import org.zowe.apiml.message.core.MessageType;
import org.zowe.apiml.message.log.ApimlLogger;
import org.zowe.apiml.product.logging.annotations.InjectApimlLogger;
import org.zowe.apiml.security.common.config.AuthConfigurationProperties;
import org.zowe.apiml.security.common.token.TokenExpireException;
import org.zowe.apiml.security.common.token.TokenNotValidException;
import org.zowe.apiml.util.Cookies;

import java.util.Optional;

/**
 * This bean provide LTPA token into request. It get LTPA from authentication source (JWT token value is set on logon)
 * and distribute it as cookie.
 */
@Component
@RequiredArgsConstructor
public class ZosmfScheme implements IAuthenticationScheme {
    @InjectApimlLogger
    private final ApimlLogger logger = ApimlLogger.empty();

    private final AuthSourceService authSourceService;
    private final AuthConfigurationProperties authConfigurationProperties;
    @Value("${apiml.security.auth.provider}")
    private String authProvider;

    @Override
    public AuthenticationScheme getScheme() {
        return AuthenticationScheme.ZOSMF;
    }

    @Override
    public AuthenticationCommand createCommand(Authentication authentication, AuthSource authSource) {
        if (!LoginProvider.ZOSMF.getValue().equals(authProvider)) {
            logger.log(MessageType.DEBUG, "ZOSMF authentication scheme is not supported for this API ML instance.");
            throw new AuthSchemeException("org.zowe.apiml.gateway.security.scheme.zosmfSchemeNotSupported");
        }

        if (authSource == null || authSource.getRawSource() == null) {
            throw new AuthSchemeException("org.zowe.apiml.gateway.security.schema.missingAuthentication");
        }

        String cookieValue = null;
        AuthSource.Parsed parsedAuthSource;
        try {
            // client cert needs to be translated to JWT in advance, so we can determine what is the source of it
            if (AuthSource.AuthSourceType.CLIENT_CERT.equals(authSource.getType())) {
                authSource = new JwtAuthSource(authSourceService.getJWT(authSource));
            }
            parsedAuthSource = authSourceService.parse(authSource);
            if (parsedAuthSource == null) {
                throw new IllegalStateException("Error occurred while parsing authenticationSource");
            }

            if (AuthSource.Origin.ZOSMF.equals(parsedAuthSource.getOrigin())) {
                logger.log(MessageType.DEBUG, "Token is generated by z/OSMF, fix set cookies.");
                cookieValue = authSourceService.getJWT(authSource);
            } else if (AuthSource.Origin.ZOWE.equals(parsedAuthSource.getOrigin())) {
                logger.log(MessageType.DEBUG, "User use Zowe own JWT token, for communication with z/OSMF there should be LTPA token, use it.");
                cookieValue = authSourceService.getLtpaToken(authSource);
            }
        } catch (TokenNotValidException e) {
            logger.log(MessageType.DEBUG, e.getLocalizedMessage());
            throw new AuthSchemeException("org.zowe.apiml.gateway.security.invalidToken");
        } catch (TokenExpireException e) {
            logger.log(MessageType.DEBUG, e.getLocalizedMessage());
            throw new AuthSchemeException("org.zowe.apiml.gateway.security.expiredToken");
        }

        final long defaultExpirationTime = System.currentTimeMillis() + authConfigurationProperties.getTokenProperties().getExpirationInSeconds() * 1000L;
        final long expirationTime = parsedAuthSource.getExpiration() != null ? parsedAuthSource.getExpiration().getTime() : defaultExpirationTime;
        final Long expireAt = Math.min(defaultExpirationTime, expirationTime);

        return new ZosmfCommand(expireAt, parsedAuthSource.getOrigin(), cookieValue);
    }

    @Override
    public Optional<AuthSource> getAuthSource() {
        return authSourceService.getAuthSourceFromRequest();
    }

    @lombok.Value
    @EqualsAndHashCode(callSuper = false)
    @RequiredArgsConstructor
    public class ZosmfCommand extends JwtCommand {

        private static final long serialVersionUID = 2284037230674275720L;
        Long expireAt;
        AuthSource.Origin authSourceOrigin;
        String cookieValue;

        @Override
        public void apply(InstanceInfo instanceInfo) {
            if (cookieValue != null && authSourceOrigin != null) {
                final RequestContext context = RequestContext.getCurrentContext();
                if (AuthSource.Origin.ZOSMF.equals(authSourceOrigin)) {
                    // token is generated by z/OSMF, fix set cookies
                    removeCookie(context, authConfigurationProperties.getCookieProperties().getCookieName());
                    setCookie(context, ZosmfService.TokenType.JWT.getCookieName(), cookieValue);
                } else if (AuthSource.Origin.ZOWE.equals(authSourceOrigin)) {
                    // user use Zowe own JWT token, for communication with z/OSMF there should be LTPA token, use it
                    setCookie(context, ZosmfService.TokenType.LTPA.getCookieName(), cookieValue);
                }
                // remove authentication part
                context.addZuulRequestHeader(HttpHeaders.AUTHORIZATION, null);
            }
        }

        @Override
        public void applyToRequest(HttpRequest request) {
            if (cookieValue != null && authSourceOrigin != null) {
                Cookies cookies = Cookies.of(request);
                if (AuthSource.Origin.ZOSMF.equals(authSourceOrigin)) {
                    cookies.remove(authConfigurationProperties.getCookieProperties().getCookieName());
                    createCookie(cookies, ZosmfService.TokenType.JWT.getCookieName(), cookieValue);
                } else if (AuthSource.Origin.ZOWE.equals(authSourceOrigin)) {
                    createCookie(cookies, ZosmfService.TokenType.LTPA.getCookieName(), cookieValue);
                }
                // remove authentication part
                request.removeHeaders(HttpHeaders.AUTHORIZATION);
            }
        }

    }

}
