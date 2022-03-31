/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.discovery;

import com.netflix.appinfo.*;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.eureka.*;
import com.netflix.eureka.resources.ServerCodecs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Nested;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.cloud.netflix.eureka.server.InstanceRegistryProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.zowe.apiml.discovery.config.EurekaConfig;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.Field;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ApimlInstanceRegistryTest {
    private ApimlInstanceRegistry apimlInstanceRegistry;

    private EurekaServerConfig serverConfig;
    private EurekaClientConfig clientConfig;
    private ServerCodecs serverCodecs;
    private EurekaClient eurekaClient;
    private InstanceRegistryProperties instanceRegistryProperties;
    private ApplicationContext appCntx;
    private InstanceInfo standardInstance;
    private EurekaConfig.Tuple tuple;

    @BeforeEach
    void setUp() throws Throwable {
        standardInstance = getStandardInstance();
        serverConfig = new DefaultEurekaServerConfig();
        clientConfig = mock(EurekaClientConfig.class);
        serverCodecs = mock(ServerCodecs.class);
        eurekaClient = mock(DiscoveryClient.class);
        instanceRegistryProperties = mock(InstanceRegistryProperties.class);
        appCntx = mock(ApplicationContext.class);
        tuple = new EurekaConfig.Tuple("service,hello");
        apimlInstanceRegistry = spy(new ApimlInstanceRegistry(
            serverConfig,
            clientConfig,
            serverCodecs,
            eurekaClient,
            instanceRegistryProperties,
            appCntx,tuple));

        MethodHandle methodHandle = mock(MethodHandle.class);
        Field declaredField = ApimlInstanceRegistry.class.getDeclaredField("handleRegistrationMethod");
        Field declaredField2 = ApimlInstanceRegistry.class.getDeclaredField("register2ArgsMethodHandle");
        Field declaredField3 = ApimlInstanceRegistry.class.getDeclaredField("register3ArgsMethodHandle");
        Field declaredField4 = ApimlInstanceRegistry.class.getDeclaredField("handleCancelationMethod");

        declaredField.setAccessible(true);
        declaredField.set(apimlInstanceRegistry, methodHandle);
        declaredField2.setAccessible(true);
        declaredField2.set(apimlInstanceRegistry, methodHandle);
        declaredField3.setAccessible(true);
        declaredField3.set(apimlInstanceRegistry, methodHandle);
        declaredField4.setAccessible(true);
        declaredField4.set(apimlInstanceRegistry, methodHandle);
    }

    @Nested
    class GivenReplacerTuple {
        @Nested
        class WhenChangeServiceId {
            @Test
            void thenChangeServicePrefix() {
                String tuple = "service,hello";
                InstanceInfo info = apimlInstanceRegistry.changeServiceId(standardInstance);
                assertEquals("hello", info.getInstanceId());
                assertEquals("HELLO", info.getAppName());
                assertEquals("HELLO", info.getVIPAddress());
                assertEquals("HELLO", info.getAppGroupName());
                assertEquals("192.168.0.1", info.getIPAddr());
                assertEquals("localhost", info.getHostName());
                assertEquals(9090, info.getSecurePort());
                assertEquals("localhost", info.getSecureVipAddress());
            }
        }

        @Nested
        class WhenInstanceIdIsDifferentFromTuple {
            @Test
            void thenDontChangeServicePrefix() {
                String tuple = "differentService,hello";
                InstanceInfo info = apimlInstanceRegistry.changeServiceId(standardInstance);
                assertEquals("service", info.getInstanceId());
                assertEquals("SERVICE", info.getAppName());
                assertEquals("SERVICE", info.getVIPAddress());
                assertEquals("SERVICE", info.getAppGroupName());
            }
        }
    }
    private static Stream<Arguments> tuples(){
       return Stream.of(
           Arguments.of("service,hello","hello"),
           Arguments.of("service,service","service"),
           Arguments.of("service","service"),
           Arguments.of("service,","service"),
           Arguments.of(null,"service")
       );
    }

    @ParameterizedTest
    @MethodSource("tuples")
    void thenInvokeChangeServiceId(String tuple, String expectedServiceIdInResult) {
        apimlInstanceRegistry = spy(new ApimlInstanceRegistry(
            serverConfig,
            clientConfig,
            serverCodecs,
            eurekaClient,
            instanceRegistryProperties,
            appCntx,new EurekaConfig.Tuple(tuple)));
        MethodHandle methodHandle = mock(MethodHandle.class);
        ReflectionTestUtils.setField(apimlInstanceRegistry,"register2ArgsMethodHandle",methodHandle);
        ReflectionTestUtils.setField(apimlInstanceRegistry,"handleRegistrationMethod",methodHandle);
        apimlInstanceRegistry.register(standardInstance, false);
        assertEquals(expectedServiceIdInResult,standardInstance.getInstanceId());
    }


        @Nested
        class WhenReplaceTupleValuesAreEquals {
            @Test
            void thenInvokeChangeServiceId() {
                ReflectionTestUtils.setField(apimlInstanceRegistry,"tuple","service,service");
                apimlInstanceRegistry.register(standardInstance, false);
                verify(apimlInstanceRegistry, times(0)).changeServiceId(any());
            }
        }

        @Nested
        class WhenReplaceTupleIsNotCorrect {
            @Test
            void thenDontInvokeServicePrefix() {
                ReflectionTestUtils.setField(apimlInstanceRegistry,"tuple","service");
                apimlInstanceRegistry.register(standardInstance, false);
                verify(apimlInstanceRegistry, times(0)).changeServiceId(any());
            }
        }

        @Nested
        class WhenReplaceTupleIsEmpty {
            @Test
            void thenDontInvokeServicePrefix() {
                ReflectionTestUtils.setField(apimlInstanceRegistry,"tuple",null);
                apimlInstanceRegistry.register(standardInstance, false);
                verify(apimlInstanceRegistry, times(0)).changeServiceId(any());
                apimlInstanceRegistry.register(standardInstance, 1, false);
                verify(apimlInstanceRegistry, times(0)).changeServiceId(any());
            }
        }

        @Nested
        class WhenReplaceTupleMissesSecondValue {
            @Test
            void thenDontInvokeServicePrefix() {
                ReflectionTestUtils.setField(apimlInstanceRegistry,"tuple","service,");
                apimlInstanceRegistry.register(standardInstance, false);
                verify(apimlInstanceRegistry, times(0)).changeServiceId(any());
                apimlInstanceRegistry.register(standardInstance, 1, false);
                verify(apimlInstanceRegistry, times(0)).changeServiceId(any());
            }
        }

        @Nested
        class WhenReplaceTupleMissesFirstValue {
            @Test
            void thenDontInvokeServicePrefix() {
                ReflectionTestUtils.setField(apimlInstanceRegistry,"tuple",",service");
                apimlInstanceRegistry.register(standardInstance, false);
                verify(apimlInstanceRegistry, times(0)).changeServiceId(any());
            }
        }

        @Nested
        class WhenRegistrationFails {
            @Test
            void thenThrowIllegalArgumentException() throws Throwable {
                MethodHandle methodHandle = mock(MethodHandle.class);
                ReflectionTestUtils.setField(apimlInstanceRegistry,"tuple","service,hello");
                ReflectionTestUtils.setField(apimlInstanceRegistry, "register2ArgsMethodHandle", methodHandle);
                when(methodHandle.invokeWithArguments(any(), any(), any())).thenThrow(new WrongMethodTypeException());
                assertThrows(IllegalArgumentException.class, () -> {
                    apimlInstanceRegistry.register(standardInstance, false);
                });
            }

            @Test
            void thenThrowIllegalArgumentException2() throws Throwable {
                MethodHandle methodHandle = mock(MethodHandle.class);
                ReflectionTestUtils.setField(apimlInstanceRegistry,"tuple","service,hello");
                ReflectionTestUtils.setField(apimlInstanceRegistry, "register2ArgsMethodHandle", methodHandle);
                when(methodHandle.invokeWithArguments(any(), any(), any())).thenThrow(new Throwable());
                assertThrows(IllegalArgumentException.class, () -> {
                    apimlInstanceRegistry.register(standardInstance, false);
                });
            }

            @Test
            void thenThrowRuntimeException() throws Throwable {
                MethodHandle methodHandle = mock(MethodHandle.class);
                ReflectionTestUtils.setField(apimlInstanceRegistry,"tuple","service,hello");
                ReflectionTestUtils.setField(apimlInstanceRegistry, "register2ArgsMethodHandle", methodHandle);
                when(methodHandle.invokeWithArguments(any(), any(), any())).thenThrow(new RuntimeException());
                assertThrows(RuntimeException.class, () -> {
                    apimlInstanceRegistry.register(standardInstance, false);
                });
            }
        }

        @Nested
        class WhenResolveInstanceRewrittenFails {
            @Test
            void thenThrowIllegalArgumentException() throws Throwable {
                MethodHandle methodHandle = mock(MethodHandle.class);
                ReflectionTestUtils.setField(apimlInstanceRegistry, "handlerResolveInstanceLeaseDurationMethod", methodHandle);
                when(methodHandle.invokeWithArguments(any(), any())).thenThrow(new WrongMethodTypeException());
                assertThrows(IllegalArgumentException.class, () -> {
                    apimlInstanceRegistry.resolveInstanceLeaseDurationRewritten(standardInstance);
                });
            }

            @Test
            void thenThrowRuntimeException() throws Throwable {
                MethodHandle methodHandle = mock(MethodHandle.class);
                ReflectionTestUtils.setField(apimlInstanceRegistry, "handlerResolveInstanceLeaseDurationMethod", methodHandle);
                when(methodHandle.invokeWithArguments(any(), any())).thenThrow(new RuntimeException());
                assertThrows(RuntimeException.class, () -> {
                    apimlInstanceRegistry.resolveInstanceLeaseDurationRewritten(standardInstance);
                });
            }

            @Test
            void thenThrowIllegalArgumentException2() throws Throwable {
                MethodHandle methodHandle = mock(MethodHandle.class);
                ReflectionTestUtils.setField(apimlInstanceRegistry, "handlerResolveInstanceLeaseDurationMethod", methodHandle);
                when(methodHandle.invokeWithArguments(any(), any())).thenThrow(new Throwable());
                assertThrows(RuntimeException.class, () -> {
                    apimlInstanceRegistry.resolveInstanceLeaseDurationRewritten(standardInstance);
                });
            }
        }

        @Nested
        class WhenSecondMethodRegistrationFails {
            @Test
            void thenThrowIllegalArgumentException() throws Throwable {
                MethodHandle methodHandle = mock(MethodHandle.class);
                ReflectionTestUtils.setField(apimlInstanceRegistry,"tuple","service,hello");
                ReflectionTestUtils.setField(apimlInstanceRegistry, "register3ArgsMethodHandle", methodHandle);
                when(methodHandle.invokeWithArguments(any(), any(), any(), any())).thenThrow(new WrongMethodTypeException());
                assertThrows(IllegalArgumentException.class, () -> {
                    apimlInstanceRegistry.register(standardInstance, 1, false);
                });
            }

            @Test
            void thenThrowIllegalArgumentException2() throws Throwable {
                MethodHandle methodHandle = mock(MethodHandle.class);
                ReflectionTestUtils.setField(apimlInstanceRegistry,"tuple","service,hello");
                ReflectionTestUtils.setField(apimlInstanceRegistry, "register3ArgsMethodHandle", methodHandle);
                when(methodHandle.invokeWithArguments(any(), any(), any(), any())).thenThrow(new Throwable());
                assertThrows(IllegalArgumentException.class, () -> {
                    apimlInstanceRegistry.register(standardInstance, 1, false);
                });
            }

            @Test
            void thenThrowRuntimeException() throws Throwable {
                MethodHandle methodHandle = mock(MethodHandle.class);
                ReflectionTestUtils.setField(apimlInstanceRegistry,"tuple","service,hello");
                ReflectionTestUtils.setField(apimlInstanceRegistry, "register2ArgsMethodHandle", methodHandle);
                when(methodHandle.invokeWithArguments(any(), any(), any())).thenThrow(new RuntimeException());
                assertThrows(RuntimeException.class, () -> {
                    apimlInstanceRegistry.register(standardInstance, false);
                });
            }
        }

        @Nested
        class WhenCancelRegistration {
            @Test
            void thenIsSuccessful() throws Throwable {
                ReflectionTestUtils.setField(apimlInstanceRegistry,"tuple","service,hello");
                MethodHandle methodHandle = mock(MethodHandle.class);
                ReflectionTestUtils.setField(apimlInstanceRegistry, "cancelMethodHandle", methodHandle);
                when(methodHandle.invokeWithArguments(any(), any(), any(), any())).thenReturn(true);
                apimlInstanceRegistry.register(standardInstance, false);
                verify(apimlInstanceRegistry, times(1)).changeServiceId(any());
                boolean isCancelled = apimlInstanceRegistry.cancel("HELLO", "hello", false);
                assertTrue(isCancelled);
            }

            @Test
            void thenThrowIllegalArgumentException() throws Throwable {
                MethodHandle methodHandle = mock(MethodHandle.class);
                ReflectionTestUtils.setField(apimlInstanceRegistry, "cancelMethodHandle", methodHandle);
                when(methodHandle.invokeWithArguments(any(), any(), any(), any())).thenThrow(new WrongMethodTypeException());
                ReflectionTestUtils.setField(apimlInstanceRegistry,"tuple","service,hello");
                assertThrows(IllegalArgumentException.class, () -> {
                    apimlInstanceRegistry.cancel("HELLO", "hello", false);
                });
            }

            @Test
            void thenThrowIllegalArgumentException2() throws Throwable {
                MethodHandle methodHandle = mock(MethodHandle.class);
                ReflectionTestUtils.setField(apimlInstanceRegistry, "cancelMethodHandle", methodHandle);
                when(methodHandle.invokeWithArguments(any(), any(), any(), any())).thenThrow(new Throwable());
                ReflectionTestUtils.setField(apimlInstanceRegistry,"tuple","service,hello");
                assertThrows(IllegalArgumentException.class, () -> {
                    apimlInstanceRegistry.cancel("HELLO", "hello", false);
                });
            }

            @Test
            void thenThrowRuntimeException() throws Throwable {
                MethodHandle methodHandle = mock(MethodHandle.class);
                ReflectionTestUtils.setField(apimlInstanceRegistry, "cancelMethodHandle", methodHandle);
                when(methodHandle.invokeWithArguments(any(), any(), any(), any())).thenThrow(new RuntimeException());
                ReflectionTestUtils.setField(apimlInstanceRegistry,"tuple","service,hello");
                assertThrows(RuntimeException.class, () -> {
                    apimlInstanceRegistry.cancel("HELLO", "hello", false);
                });
            }
        }


    private InstanceInfo getStandardInstance() {

        return InstanceInfo.Builder.newBuilder()
            .setInstanceId("service")
            .setAppName("SERVICE")
            .setAppGroupName("SERVICE")
            .setIPAddr("192.168.0.1")
            .enablePort(InstanceInfo.PortType.SECURE, true)
            .setSecurePort(9090)
            .setHostName("localhost")
            .setSecureVIPAddress("localhost")
            .setVIPAddress("SERVICE")
            .setStatus(InstanceInfo.InstanceStatus.UP)
            .build();
    }
}
