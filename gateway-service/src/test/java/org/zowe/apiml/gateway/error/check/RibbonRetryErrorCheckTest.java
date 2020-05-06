/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.gateway.error.check;

import com.netflix.zuul.exception.ZuulException;
import com.netflix.zuul.monitoring.MonitoringHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AuthorizationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.zowe.apiml.gateway.config.MessageServiceConfiguration;
import org.zowe.apiml.gateway.error.ErrorUtils;
import org.zowe.apiml.gateway.error.InternalServerErrorController;
import org.zowe.apiml.gateway.ribbon.http.RequestAbortException;
import org.zowe.apiml.gateway.ribbon.http.RequestContextNotPreparedException;
import org.zowe.apiml.message.api.ApiMessage;
import org.zowe.apiml.message.api.ApiMessageView;
import org.zowe.apiml.message.core.MessageService;

import java.net.ConnectException;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {MessageServiceConfiguration.class})
class RibbonRetryErrorCheckTest {

    private static InternalServerErrorController underTest;

    @Autowired
    private MessageService messageService;

    @BeforeAll
    public static void setupAll() {
        MonitoringHelper.initMocks();
    }

    @BeforeEach
    public void setup() {
        underTest = new InternalServerErrorController(messageService);
    }

    private static Stream<Arguments> provideExceptionsWithRelevantTexts() {
        return Stream.of(
            Arguments.of("givenExceptionChain_whenIsAbortException_thenRequestAbortedGeneric",
                new RequestAbortException("test"),
                "The request to the URL 'null' has been aborted without retrying on another instance. Caused by: org.zowe.apiml.gateway.ribbon.http.RequestAbortException: test",
                "org.zowe.apiml.gateway.requestAborted"),
            Arguments.of("givenExceptionChain_whenIsAbortExceptionWithCause_thenRequestAbortedGenericAndCause",
                new RequestAbortException(new AuthorizationServiceException("test")),
                "The request to the URL 'null' has been aborted without retrying on another instance. Caused by: org.zowe.apiml.gateway.ribbon.http.RequestAbortException: org.springframework.security.access.AuthorizationServiceException: test, Caused by: org.springframework.security.access.AuthorizationServiceException: test",
                "org.zowe.apiml.gateway.requestAborted"),
            Arguments.of("givenExceptionChainWithTwoNestedExceptions_whenIsAbortExceptionWithCause_thenRequestAbortedGenericAndCause",
                new RequestAbortException(new AuthorizationServiceException("msg", new BadCredentialsException("test"))),
                "The request to the URL 'null' has been aborted without retrying on another instance. Caused by: org.zowe.apiml.gateway.ribbon.http.RequestAbortException: org.springframework.security.access.AuthorizationServiceException: msg, Caused by: org.springframework.security.access.AuthorizationServiceException: msg, Caused by: org.springframework.security.authentication.BadCredentialsException: test",
                "org.zowe.apiml.gateway.requestAborted"),
            Arguments.of("givenExceptionChain_whenIsContextNotPreparedExceptionWithCause_thenContextNotPreparedAndCause",
                new RequestContextNotPreparedException("RequestContext not prepared for load balancing."),
                "RequestContext not prepared for load balancing.",
                "org.zowe.apiml.gateway.contextNotPrepared"),
            Arguments.of("givenExceptionChain_whenIsConnectionException_thenConnectionExceptionAndCause",
                new ConnectException("test"),
                "The request to the URL 'null' has failed after retrying on all known service instances. Caused by: null",
                "org.zowe.apiml.gateway.connectionRefused")
        );
    }


    @ParameterizedTest(name = "{0}")
    @MethodSource("provideExceptionsWithRelevantTexts")
    void givenExceptionChain_whenExceptionIsRaised_thenRequestIsProperlyAborted(String description, Exception toWrap, String message, String key) {
        MockHttpServletRequest request = new MockHttpServletRequest();

        ZuulException exc = new ZuulException(new Exception(toWrap), HttpStatus.INTERNAL_SERVER_ERROR.value(), "");
        request.setAttribute(ErrorUtils.ATTR_ERROR_EXCEPTION, exc);
        ResponseEntity<ApiMessageView> response = underTest.error(request);

        assertCorrectResponse(response, message, HttpStatus.INTERNAL_SERVER_ERROR, key);
    }

    private void assertCorrectResponse(ResponseEntity<ApiMessageView> response, String expectedMessage, HttpStatus expectedStatus, String expectedKey) {
        assertThat(response.getStatusCodeValue(), is(expectedStatus.value()));
        ApiMessage firstMessage = response.getBody().getMessages().get(0);
        assertThat(firstMessage.getMessageKey(), is(expectedKey));
        assertThat(firstMessage.getMessageContent(), containsString(expectedMessage));
    }
}
