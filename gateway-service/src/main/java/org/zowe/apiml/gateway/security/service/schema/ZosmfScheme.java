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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.zowe.apiml.auth.Authentication;
import org.zowe.apiml.auth.AuthenticationScheme;
import org.zowe.apiml.gateway.security.login.LoginProvider;
import org.zowe.apiml.gateway.security.service.schema.source.AuthSource;
import org.zowe.apiml.gateway.security.service.schema.source.AuthSourceService;
import org.zowe.apiml.gateway.security.service.schema.source.JwtAuthSource;
import org.zowe.apiml.gateway.security.service.schema.source.UserNotMappedException;
import org.zowe.apiml.gateway.security.service.zosmf.ZosmfService;
import org.zowe.apiml.message.core.MessageService;
import org.zowe.apiml.security.common.config.AuthConfigurationProperties;
import org.zowe.apiml.security.common.error.AuthenticationTokenException;
import org.zowe.apiml.security.common.token.TokenExpireException;
import org.zowe.apiml.security.common.token.TokenNotValidException;

import java.util.Date;
import java.util.Optional;

import static org.zowe.apiml.gateway.security.service.schema.JwtCommand.AUTH_FAIL_HEADER;

/**
 * This bean provide LTPA token into request. It get LTPA from authentication source (JWT token value is set on logon)
 * and distribute it as cookie.
 */
@Component
@RequiredArgsConstructor
public class ZosmfScheme implements IAuthenticationScheme {

    private final AuthSourceService authSourceService;
    private final AuthConfigurationProperties authConfigurationProperties;
    private final MessageService messageService;
    @Value("${apiml.security.auth.provider}")
    private String authProvider;

    @Override
    public AuthenticationScheme getScheme() {
        return AuthenticationScheme.ZOSMF;
    }

    @Override
    public AuthenticationCommand createCommand(Authentication authentication, AuthSource authSource) {
        if (!LoginProvider.ZOSMF.getValue().equals(authProvider)) {
            String error = this.messageService.createMessage("org.zowe.apiml.gateway.security.scheme.zosmfSchemeNotSupported").mapToLogMessage();
            return new ZosmfCommand(null, error);
        }

        final RequestContext context = RequestContext.getCurrentContext();
        // Check for error in context to use it in header "X-Zowe-Auth-Failure"
        if (context.containsKey(AUTH_FAIL_HEADER)) {
            String errorHeaderValue = context.get(AUTH_FAIL_HEADER).toString();
            // this command should expire immediately after creation because it is build based on missing/incorrect authentication
            return new ZosmfCommand(System.currentTimeMillis(), errorHeaderValue);
        }

        if (authSource == null || authSource.getRawSource() == null) {
            String error = this.messageService.createMessage("org.zowe.apiml.gateway.security.schema.missingAuthentication").mapToLogMessage();
            return new ZosmfCommand(null, error);
        }

        String error = null;
        String cookieValue = null;
        AuthSource.Parsed parsedAuthSource = null;

        try {
            // client cert needs to be translated to JWT in advance, so we can determine what is the source of it
            if (AuthSource.AuthSourceType.CLIENT_CERT.equals(authSource.getType())) {
                authSource = new JwtAuthSource(authSourceService.getJWT(authSource));
            }
            parsedAuthSource = authSourceService.parse(authSource);
            if (parsedAuthSource == null) {
                error = this.messageService.createMessage("org.zowe.apiml.gateway.security.scheme.x509ParsingError", "Cannot parse provided authentication source").mapToLogMessage();
            } else {
                if (AuthSource.Origin.ZOSMF.equals(parsedAuthSource.getOrigin())) {
                    // token is generated by z/OSMF, fix set cookies
                    cookieValue = authSourceService.getJWT(authSource);
                } else if (AuthSource.Origin.ZOWE.equals(parsedAuthSource.getOrigin())) {
                    // user use Zowe own JWT token, for communication with z/OSMF there should be LTPA token, use it
                    cookieValue = authSourceService.getLtpaToken(authSource);
                }
            }
        } catch (TokenNotValidException e) {
            error = this.messageService.createMessage("org.zowe.apiml.gateway.security.invalidToken").mapToLogMessage();
        } catch (TokenExpireException e) {
            error = this.messageService.createMessage("org.zowe.apiml.gateway.security.expiredToken").mapToLogMessage();
        } catch (UserNotMappedException | AuthenticationTokenException e) {
            error = this.messageService.createMessage(e.getMessage()).mapToLogMessage();
        }

        if (error != null) {
            return new ZosmfCommand(null, error);
        }

        final long defaultExpirationTime = System.currentTimeMillis() + authConfigurationProperties.getTokenProperties().getExpirationInSeconds() * 1000L;
        final Date expiration = parsedAuthSource == null ? null : parsedAuthSource.getExpiration();
        final Long expirationTime = expiration != null ? expiration.getTime() : null;
        final Long expireAt = expirationTime != null ? Math.min(defaultExpirationTime, expirationTime) : null;
        final AuthSource.Origin origin = parsedAuthSource != null ? parsedAuthSource.getOrigin() : null;

        return new ZosmfCommand(expireAt, origin, cookieValue, error);
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
        String errorHeaderValue;

        public ZosmfCommand(Long expireAt, String errorHeaderValue) {
            this.expireAt = expireAt;
            this.authSourceOrigin = null;
            cookieValue = null;
            this.errorHeaderValue = errorHeaderValue;
        }

        @Override
        public void apply(InstanceInfo instanceInfo) {
            final RequestContext context = RequestContext.getCurrentContext();

            if (cookieValue != null && authSourceOrigin != null) {
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
            } else {
                JwtCommand.setErrorHeader(context, errorHeaderValue);
            }
        }

    }

}
