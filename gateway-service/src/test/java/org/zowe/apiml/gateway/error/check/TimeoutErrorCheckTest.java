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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.zowe.apiml.gateway.error.ErrorUtils;
import org.zowe.apiml.gateway.error.InternalServerErrorController;
import org.zowe.apiml.message.api.ApiMessageView;
import org.zowe.apiml.message.core.MessageService;
import org.zowe.apiml.message.yaml.YamlMessageService;
import com.netflix.zuul.exception.ZuulException;
import com.netflix.zuul.monitoring.MonitoringHelper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.net.SocketTimeoutException;

public class TimeoutErrorCheckTest {
    private static final String TEST_MESSAGE = "Hello";
    private static InternalServerErrorController errorController;

    @BeforeAll
    public static void setup() {
        MonitoringHelper.initMocks();
        MessageService messageService = new YamlMessageService();
        errorController = new InternalServerErrorController(messageService);
    }

    private void assertCorrectMessage(ResponseEntity<ApiMessageView> response, String expectedMessage) {
        assertEquals(HttpStatus.GATEWAY_TIMEOUT.value(), response.getStatusCodeValue());
        assertEquals("org.zowe.apiml.common.serviceTimeout", response.getBody().getMessages().get(0).getMessageKey());
        assertTrue(response.getBody().getMessages().get(0).getMessageContent().contains(expectedMessage));
    }

    @Test
    void testZuulTimeoutError() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        ZuulException exc = new ZuulException(new Exception(TEST_MESSAGE), HttpStatus.GATEWAY_TIMEOUT.value(), null);
        request.setAttribute(ErrorUtils.ATTR_ERROR_EXCEPTION, exc);
        ResponseEntity<ApiMessageView> response = errorController.error(request);

        assertCorrectMessage(response, TEST_MESSAGE);
    }

    @Test
    void testZuulTimeoutErrorWithoutCause() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        ZuulException exc = new ZuulException("", HttpStatus.GATEWAY_TIMEOUT.value(), "TEST");
        request.setAttribute(ErrorUtils.ATTR_ERROR_EXCEPTION, exc);

        ResponseEntity<ApiMessageView> response = errorController.error(request);

        assertCorrectMessage(response, TimeoutErrorCheck.DEFAULT_MESSAGE);
    }

    @Test
    void testZuulSocketTimeoutError() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        ZuulException exc = new ZuulException(new SocketTimeoutException(TEST_MESSAGE),
                HttpStatus.INTERNAL_SERVER_ERROR.value(), "TEST");
        request.setAttribute(ErrorUtils.ATTR_ERROR_EXCEPTION, exc);

        ResponseEntity<ApiMessageView> response = errorController.error(request);

        assertCorrectMessage(response, TEST_MESSAGE);
    }
}
