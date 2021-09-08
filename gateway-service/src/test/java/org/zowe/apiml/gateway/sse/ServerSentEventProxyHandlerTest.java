/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.gateway.sse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServerSentEventProxyHandlerTest {
    private static final String SERVICE_ID = "serviceid";
    private static final String GATEWAY_PATH = "/" + SERVICE_ID + "/sse/v1/endpoint";
    private static final String GATEWAY_PATH_OLD_FORMAT = "/sse/v1/" + SERVICE_ID + "/endpoint";

    private ServerSentEventProxyHandler underTest;
    private DiscoveryClient mockDiscoveryClient;
    private HttpServletRequest mockHttpServletRequest;
    private HttpServletResponse mockHttpServletResponse;

    @BeforeEach
    public void setup() {
        mockHttpServletRequest = mock(HttpServletRequest.class);
        mockHttpServletResponse = mock(HttpServletResponse.class);

        mockDiscoveryClient = mock(DiscoveryClient.class);
        underTest = Mockito.spy(new ServerSentEventProxyHandler(mockDiscoveryClient));
    }

    @Nested
    class WhenGetEmitter {
        @Nested
        class GivenBadUri_thenReturnEmitter {
            @Test
            void givenNoUriParts() throws IOException {
                when(mockHttpServletRequest.getRequestURI()).thenReturn(null);
                verifyConsumersNotUsed();
            }

            @Test
            void givenShortUri() throws IOException {
                when(mockHttpServletRequest.getRequestURI()).thenReturn("/notenoughparts");
                verifyConsumersNotUsed();
            }

            private void verifyConsumersNotUsed() throws IOException {
                SseEmitter result = underTest.getEmitter(mockHttpServletRequest, mockHttpServletResponse);
                assertThat(result, is(not(nullValue())));
                verify(underTest, times(0)).consumer(any());
                verify(underTest, times(0)).error(any());
            }
        }

        @Test
        void givenNoServiceInstances_thenReturnNull() throws IOException {
            when(mockHttpServletRequest.getRequestURI()).thenReturn(GATEWAY_PATH);

            PrintWriter mockWriter = mock(PrintWriter.class);
            when(mockDiscoveryClient.getInstances(anyString())).thenReturn(new ArrayList<>());
            when(mockHttpServletResponse.getWriter()).thenReturn(mockWriter);

            SseEmitter result = underTest.getEmitter(mockHttpServletRequest, mockHttpServletResponse);
            assertThat(result, is(nullValue()));
            verify(mockWriter, times(1)).print(anyString());
        }

        @Test
        void givenSameServiceTwice_thenUtilizeConsumersTwice() throws IOException {
            when(mockHttpServletRequest.getRequestURI()).thenReturn(GATEWAY_PATH);
            mockOneServiceInstance();

            verifyConsumersUsed();
            verifyConsumersUsed();
            verifyTargetUrlUsed(GATEWAY_PATH);
        }

        @Nested
        class ThenUtilizeConsumers {
            @Test
            void givenEndpoint() throws IOException {
                when(mockHttpServletRequest.getRequestURI()).thenReturn(GATEWAY_PATH);
                mockOneServiceInstance();

                verifyConsumersUsed();
                verifyTargetUrlUsed(GATEWAY_PATH);
            }

            @Test
            void givenNoEndpointPath() throws IOException {
                String path = "/serviceid/sse/v1/";
                when(mockHttpServletRequest.getRequestURI()).thenReturn(path);
                mockOneServiceInstance();

                verifyConsumersUsed();
                verifyTargetUrlUsed(path);
            }

            @Test
            void givenPathInEndpoint() throws IOException {
                String path = GATEWAY_PATH + "/anotherendpoint";
                when(mockHttpServletRequest.getRequestURI()).thenReturn(path);
                mockOneServiceInstance();

                verifyConsumersUsed();
                verifyTargetUrlUsed(path);
            }

            @Test
            void givenOldPathFormat() throws IOException {
                String path = GATEWAY_PATH_OLD_FORMAT;
                when(mockHttpServletRequest.getRequestURI()).thenReturn(path);
                mockOneServiceInstance();

                verifyConsumersUsed();
                verifyTargetUrlUsed(path);
            }
        }

        @Nested
        class WhenUseDataConsumer {
            @Test
            void givenContent_thenEmitData() throws IOException {
                SseEmitter mockEmitter = mock(SseEmitter.class);
                Consumer<ServerSentEvent<String>> result = underTest.consumer(mockEmitter);

                ServerSentEvent<String> event = ServerSentEvent.builder("event").build();
                result.accept(event);

                verify(mockEmitter).send(anyString());
            }

            @Test
            void givenIOExceptionWhenSendContent_thenCompleteStream() throws IOException {
                SseEmitter mockEmitter = mock(SseEmitter.class);
                Consumer<ServerSentEvent<String>> result = underTest.consumer(mockEmitter);

                doThrow(new IOException("error")).when(mockEmitter).send(anyString());

                ServerSentEvent<String> event = ServerSentEvent.builder("event").build();
                result.accept(event);

                verify(mockEmitter).complete();
            }
        }

        @Nested
        class WhenUseErrorConsumer {
            @Test
            void givenError_thenCompleteStream() {
                SseEmitter mockEmitter = mock(SseEmitter.class);
                Consumer<Throwable> result = underTest.error(mockEmitter);
                result.accept(new Exception("error"));

                verify(mockEmitter).complete();
            }
        }

        private void mockOneServiceInstance() {
            List<ServiceInstance> serviceInstances = new ArrayList<>();
            serviceInstances.add(mock(ServiceInstance.class));
            when(mockDiscoveryClient.getInstances(anyString())).thenReturn(serviceInstances);
        }

        private void verifyConsumersUsed() throws IOException {
            SseEmitter emitter = underTest.getEmitter(mockHttpServletRequest, mockHttpServletResponse);
            assertThat(emitter, is(not(nullValue())));
            verify(underTest).consumer(emitter);
            verify(underTest).error(emitter);
        }

        private void verifyTargetUrlUsed(String expectedUrlPath) {
            Set<String> usedUrls = underTest.getSseEventStreams().keySet();
            boolean used = false;
            for (String url : usedUrls) {
                used = url.contains(expectedUrlPath);
                if (used) {
                    break;
                }
            }
            assertThat("Expected path '" + expectedUrlPath + "' was not used", used, is(true));
        }
    }
}
