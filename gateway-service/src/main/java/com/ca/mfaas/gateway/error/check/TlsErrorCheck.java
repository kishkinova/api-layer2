/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package com.ca.mfaas.gateway.error.check;

import com.ca.mfaas.error.ErrorService;
import com.ca.mfaas.gateway.error.ErrorUtils;
import com.ca.mfaas.rest.response.ApiMessage;
import com.netflix.zuul.exception.ZuulException;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.net.ssl.SSLException;
import javax.servlet.http.HttpServletRequest;

/**
 * Checks whether the error was caused by timeout (service not responding).
 */
@Slf4j
public class TlsErrorCheck implements ErrorCheck {
    private final ErrorService errorService;

    public TlsErrorCheck(ErrorService errorService) {
        this.errorService = errorService;
    }

    public ResponseEntity<ApiMessage> checkError(HttpServletRequest request, Throwable exc) {
        if (exc instanceof ZuulException) {
            int exceptionIndex = ExceptionUtils.indexOfType(exc, SSLException.class);
            if (exceptionIndex != -1) {
                Throwable sslException = ExceptionUtils.getThrowables(exc)[exceptionIndex];
                log.debug("TLS request error: {}", sslException.getMessage(), sslException);
                return tlsErrorResponse(request, sslException.getMessage());
            }
        }

        return null;
    }

    private ResponseEntity<ApiMessage> tlsErrorResponse(HttpServletRequest request, String message) {
        ApiMessage apiMessage = errorService.createApiMessage("apiml.common.tlsError", ErrorUtils.getGatewayUri(request),
                message);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(apiMessage);
    }
}
