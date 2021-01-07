/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.discovery.metadata;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.eureka.EurekaServerContext;
import com.netflix.eureka.EurekaServerContextHolder;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.netflix.eureka.server.event.EurekaInstanceRegisteredEvent;
import org.zowe.apiml.discovery.EurekaInstanceRegisteredListener;
import org.zowe.apiml.discovery.GatewayNotifier;
import org.zowe.apiml.discovery.staticdef.ServiceDefinitionProcessor;
import org.zowe.apiml.discovery.staticdef.StaticRegistrationResult;
import org.zowe.apiml.discovery.staticdef.StaticServicesRegistrationService;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.zowe.apiml.constants.EurekaMetadataDefinition.AUTHENTICATION_APPLID;

@ExtendWith(MockitoExtension.class)
class MetadataDefaultsServiceTest {

    @InjectMocks
    private StaticServicesRegistrationService staticServicesRegistrationService;

    @Spy
    @InjectMocks
    private EurekaInstanceRegisteredListener eurekaInstanceRegisteredListener;

    @Spy
    private MetadataTranslationService metadataTranslationService;

    @Spy
    private MetadataDefaultsService metadataDefaultsService;

    @Spy
    private ServiceDefinitionProcessorMock serviceDefinitionProcessor;

    @Mock
    private GatewayNotifier gatewayNotifier;

    private PeerAwareInstanceRegistry mockRegistry;

    @BeforeEach
    void setUp() {
        mockRegistry = mock(PeerAwareInstanceRegistry.class);
        doAnswer(x -> {
            EurekaInstanceRegisteredEvent event = mock(EurekaInstanceRegisteredEvent.class);
            when(event.getInstanceInfo()).thenReturn(x.getArgument(0));
            eurekaInstanceRegisteredListener.listen(event);
            return mockRegistry;
        }).when(mockRegistry).register(any(), anyBoolean());
        EurekaServerContext mockEurekaServerContext = mock(EurekaServerContext.class);
        when(mockEurekaServerContext.getRegistry()).thenReturn(mockRegistry);
        EurekaServerContextHolder.initialize(mockEurekaServerContext);
    }

    @Test
    void testUpdating() {
        serviceDefinitionProcessor.setLocation("api-defs");

        staticServicesRegistrationService.reloadServices();
        Map<String, InstanceInfo> map = staticServicesRegistrationService.getStaticInstances().stream()
            .collect(Collectors.toMap(InstanceInfo::getId, Function.identity()));

        assertEquals(
            "TSTAPPL4",
            map.get("STATIC-localhost:toAddAuth:10012").getMetadata().get(AUTHENTICATION_APPLID)
        );

        assertEquals(
            "TSTAPPL5",
            map.get("STATIC-localhost:toReplaceAuth:10012").getMetadata().get(AUTHENTICATION_APPLID)
        );

        assertEquals(
            "TSTAPPL3",
            map.get("STATIC-localhost:nowFixedAuth:10012").getMetadata().get(AUTHENTICATION_APPLID)
        );
    }

    class ServiceDefinitionProcessorMock extends ServiceDefinitionProcessor {

        private String location;

        public void setLocation(String location) {
            this.location = location;
        }

        protected List<File> getFiles(StaticRegistrationResult context, String staticApiDefinitionsDirectories) {
            try {
                return Collections.singletonList(Paths.get(ClassLoader.getSystemResource(location).toURI()).toFile());
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }

    }

}
