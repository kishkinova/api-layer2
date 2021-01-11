/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.apicatalog.services.cached;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Application;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.zowe.apiml.apicatalog.model.APIContainer;
import org.zowe.apiml.apicatalog.model.APIService;
import org.zowe.apiml.product.gateway.GatewayClient;
import org.zowe.apiml.product.routing.RoutedServices;
import org.zowe.apiml.product.routing.ServiceType;
import org.zowe.apiml.product.routing.transform.TransformService;
import org.zowe.apiml.product.routing.transform.URLTransformationException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.zowe.apiml.constants.EurekaMetadataDefinition.*;

@SuppressWarnings({"squid:S2925"}) // replace with proper wait test library
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CachedProductFamilyTest {
    private final Integer cacheRefreshUpdateThresholdInMillis = 2000;

    private CachedProductFamilyService service;

    @Mock
    private final TransformService transformService = new TransformService(new GatewayClient(null));

    @BeforeEach
    void setup() {
        service = new CachedProductFamilyService(
            null,
            transformService,
            cacheRefreshUpdateThresholdInMillis);
    }

    @Test
    void testRetrievalOfRecentlyUpdatedContainers() {
        service.getContainer("demoapp", createApp("service1", "demoapp"));
        service.getContainer("demoapp2", createApp("service2", "demoapp2"));

        Collection<APIContainer> containers = service.getRecentlyUpdatedContainers();
        assertEquals(2, containers.size());
    }

    @Test
    void testRetrievalOfRecentlyUpdatedContainersExcludeOldUpdate() throws InterruptedException {
        // To speed up the test, create instance which consider even 5 milliseconds as old.
        service = new CachedProductFamilyService(
            null,
            transformService,
            5);
        // This is considered as old update.
        service.getContainer("demoapp", createApp("service1", "demoapp"));

        Thread.sleep(10);

        service.getContainer("demoapp2", createApp("service2", "demoapp2"));

        Collection<APIContainer> containers = service.getRecentlyUpdatedContainers();
        assertEquals(1, containers.size());
    }

    @Test
    void testRetrievalOfRecentlyUpdatedContainersExcludeAll() throws InterruptedException {
        // To speed up the test, create instance which consider even 5 milliseconds as old.
        service = new CachedProductFamilyService(
            null,
            transformService,
            5);
        service.getContainer("demoapp", createApp("service1", "demoapp"));
        service.getContainer("demoapp2", createApp("service2", "demoapp2"));

        Thread.sleep(10);

        Collection<APIContainer> containers = service.getRecentlyUpdatedContainers();
        assertTrue(containers.isEmpty());
    }

    @Test
    void testRetrievalOfContainerServices() {
        InstanceInfo instance1 = createApp("service1", "demoapp");
        service.getContainer("demoapp", instance1);

        InstanceInfo instance2 = createApp("service2", "demoapp2");
        service.addServiceToContainer("demoapp", instance2);

        APIService containerService = service.getContainerService("demoapp", instance1);
        assertEquals("service1", containerService.getServiceId());

        containerService = service.getContainerService("demoapp", instance2);
        assertEquals("service2", containerService.getServiceId());
    }

    @Test
    void testCreationOfContainerWithoutInstance() {
        assertThrows(NullPointerException.class, () -> {
            service.getContainer("demoapp", null);
        });
        assertEquals(0, service.getContainerCount());
        assertEquals(0, service.getAllContainers().size());
    }

    @Test
    void testGetMultipleContainersForASingleService() {
        InstanceInfo instance = createApp("service1", "demoapp");
        service.getContainer("demoapp1", instance);
        service.getContainer("demoapp2", instance);

        List<APIContainer> containersForService = service.getContainersForService("service1");
        assertEquals(2, containersForService.size());
        assertEquals(2, service.getContainerCount());
        assertEquals(2, service.getAllContainers().size());
    }

    @Test
    void testCallCreationOfContainerThatAlreadyExistsButNothingHasChangedSoNoUpdate() {
        InstanceInfo instance = createApp("service1", "demoapp");
        APIContainer originalContainer = service.getContainer("demoapp", instance);
        Calendar createTimestamp = originalContainer.getLastUpdatedTimestamp();

        APIContainer updatedContainer = service.createContainerFromInstance("demoapp", instance);
        Calendar updatedTimestamp = updatedContainer.getLastUpdatedTimestamp();

        boolean equals = updatedTimestamp.equals(createTimestamp);
        assertTrue(equals);
    }

    @Test
    void testCallCreationOfContainerThatAlreadyExistsInstanceInfoHasChangedSoUpdateLastChangeTime() throws InterruptedException {
        APIContainer originalContainer = service.getContainer("demoapp", createApp("service", "demoapp"));
        Calendar createTimestamp = originalContainer.getLastUpdatedTimestamp();

        Thread.sleep(100);

        APIContainer updatedContainer = service.createContainerFromInstance("demoapp",
            createApp("service",
                "demoapp",
                "Title 2",
                "Description 2",
                "1.0.1",
                InstanceInfo.InstanceStatus.UP));
        Calendar updatedTimestamp = updatedContainer.getLastUpdatedTimestamp();

        boolean equals = updatedTimestamp.equals(createTimestamp);
        assertFalse(equals);

        Thread.sleep(100);

        service.updateContainerFromInstance("demoapp",
            createApp("service",
                "demoapp",
                "Title 2",
                "Description 2",
                "2.0.0",
                InstanceInfo.InstanceStatus.UP));
        Calendar retrievedTimestamp = updatedContainer.getLastUpdatedTimestamp();

        equals = updatedTimestamp.equals(retrievedTimestamp);
        assertFalse(equals);
    }

    @Test
    void givenInstanceIsIsInContainer_WhenNewVersionIsProvided_ThenContainerMetadataIsUpdated() {
        // Create the initial container
        String serviceId = "apptoupdate",
            catalogId = "demoapp";
        APIContainer container =
            service.createContainerFromInstance(serviceId, createApp(serviceId, catalogId));

        String newTitle = "New Title";
        service.updateContainerFromInstance(serviceId, createApp(serviceId, catalogId,
            "1.0.1", newTitle));

        assertEquals(container.getTitle(), newTitle);
    }

    @Test
    void testCalculationOfContainerTotalsWithAllServicesUp() {
        CachedServicesService cachedServicesService = Mockito.mock(CachedServicesService.class);

        InstanceInfo instance1 = createApp("service", "demoapp");
        InstanceInfo instance2 = createApp("service", "demoapp");
        Application application = new Application();
        application.addInstance(instance1);
        application.addInstance(instance2);

        when(cachedServicesService.getService("service")).thenReturn(application);
        service = new CachedProductFamilyService(
            cachedServicesService,
            transformService,
            cacheRefreshUpdateThresholdInMillis);

        service.getContainer("demoapp", instance1);
        service.addServiceToContainer("demoapp", instance2);

        List<APIContainer> containersForService = service.getContainersForService("service");
        assertEquals(1, service.getContainerCount());

        APIContainer container = containersForService.get(0);
        service.calculateContainerServiceTotals(container);
        assertEquals("UP", container.getStatus());
        assertEquals(1, container.getTotalServices().intValue());
        assertEquals(1, container.getActiveServices().intValue());
    }

    @Test
    void testCalculationOfContainerTotalsWithAllServicesDown() {
        CachedServicesService cachedServicesService = Mockito.mock(CachedServicesService.class);

        InstanceInfo instance1 = createApp("service1", "demoapp", InstanceInfo.InstanceStatus.DOWN);
        InstanceInfo instance2 = createApp("service2", "demoapp", InstanceInfo.InstanceStatus.DOWN);
        Application application1 = new Application();
        application1.addInstance(instance1);
        Application application2 = new Application();
        application2.addInstance(instance2);

        when(cachedServicesService.getService("service1")).thenReturn(application1);
        when(cachedServicesService.getService("service2")).thenReturn(application2);
        service = new CachedProductFamilyService(
            cachedServicesService,
            transformService,
            cacheRefreshUpdateThresholdInMillis);

        service.getContainer("demoapp", instance1);
        service.addServiceToContainer("demoapp", instance2);

        APIContainer container = service.retrieveContainer("demoapp");
        assertNotNull(container);

        service.calculateContainerServiceTotals(container);
        assertEquals("DOWN", container.getStatus());
        assertEquals(2, container.getTotalServices().intValue());
        assertEquals(0, container.getActiveServices().intValue());
    }

    @Test
    void testCalculationOfContainerTotalsWithSomeServicesDown() {
        CachedServicesService cachedServicesService = Mockito.mock(CachedServicesService.class);

        InstanceInfo instance1 = createApp("service1", "demoapp", InstanceInfo.InstanceStatus.UP);
        InstanceInfo instance2 = createApp("service2", "demoapp", InstanceInfo.InstanceStatus.DOWN);
        Application application1 = new Application();
        application1.addInstance(instance1);
        Application application2 = new Application();
        application2.addInstance(instance2);

        when(cachedServicesService.getService("service1")).thenReturn(application1);
        when(cachedServicesService.getService("service2")).thenReturn(application2);
        service = new CachedProductFamilyService(
            cachedServicesService,
            transformService,
            cacheRefreshUpdateThresholdInMillis);

        service.getContainer("demoapp", instance1);
        service.addServiceToContainer("demoapp", instance2);

        APIContainer container = service.retrieveContainer("demoapp");
        assertNotNull(container);

        service.calculateContainerServiceTotals(container);
        assertEquals("WARNING", container.getStatus());
        assertEquals(2, container.getTotalServices().intValue());
        assertEquals(1, container.getActiveServices().intValue());
    }

    @Test
    void givenInstanceIsNotInTheCache_whenCallSaveContainerFromInstance_thenCreateNew()
        throws URLTransformationException {

        HashMap<String, String> metadata = new HashMap<>();
        metadata.put(CATALOG_ID, "demoapp");
        metadata.put(CATALOG_TITLE, "Title");
        metadata.put(CATALOG_DESCRIPTION, "Description");
        metadata.put(CATALOG_VERSION, "1.0.0");
        metadata.put(SERVICE_TITLE, "sTitle");
        metadata.put(SERVICE_DESCRIPTION, "sDescription");
        InstanceInfo instance = getStandardInstance("service1", InstanceInfo.InstanceStatus.UP, metadata);

        when(transformService.transformURL(
            any(ServiceType.class), any(String.class), any(String.class), any(RoutedServices.class)
        )).thenReturn(instance.getHomePageUrl());

        APIContainer actualDemoAppContainer = service.saveContainerFromInstance("demoapp", instance);

        List<APIContainer> lsContainer = service.getRecentlyUpdatedContainers();
        assertEquals(1, lsContainer.size());

        assertEquals(metadata.get(CATALOG_ID), actualDemoAppContainer.getId());
        assertEquals(metadata.get(CATALOG_TITLE), actualDemoAppContainer.getTitle());
        assertEquals(metadata.get(CATALOG_DESCRIPTION), actualDemoAppContainer.getDescription());
        assertEquals(metadata.get(CATALOG_VERSION), actualDemoAppContainer.getVersion());

        Set<APIService> apiServices = actualDemoAppContainer.getServices();
        assertEquals(1, apiServices.size());

        APIService actualService = apiServices.iterator().next();
        assertEquals(instance.getAppName().toLowerCase(), actualService.getServiceId());
        assertEquals(metadata.get(SERVICE_TITLE), actualService.getTitle());
        assertEquals(metadata.get(SERVICE_DESCRIPTION), actualService.getDescription());
        assertEquals(instance.isPortEnabled(InstanceInfo.PortType.SECURE), actualService.isSecured());
        assertEquals(instance.getHomePageUrl(), actualService.getHomePageUrl());
    }

    @Test
    void givenInstanceIsInTheCache_whenCallSaveContainerFromInstance_thenUpdate()
        throws InterruptedException, URLTransformationException {

        HashMap<String, String> metadata = new HashMap<>();
        metadata.put(CATALOG_ID, "demoapp");
        metadata.put(CATALOG_TITLE, "Title");
        metadata.put(CATALOG_DESCRIPTION, "Description");
        metadata.put(CATALOG_VERSION, "1.0.0");
        metadata.put(SERVICE_TITLE, "sTitle");
        metadata.put(SERVICE_DESCRIPTION, "sDescription");
        InstanceInfo instance = getStandardInstance("service1", InstanceInfo.InstanceStatus.UP, metadata);

        when(transformService.transformURL(
            any(ServiceType.class), any(String.class), any(String.class), any(RoutedServices.class)
        )).thenReturn(instance.getHomePageUrl());

        APIContainer actualDemoAppContainer = service.saveContainerFromInstance("demoapp", instance);
        Calendar createTimestamp = actualDemoAppContainer.getLastUpdatedTimestamp();

        Thread.sleep(100);

        metadata.put(CATALOG_TITLE, "Title2");
        metadata.put(CATALOG_DESCRIPTION, "Description2");
        metadata.put(CATALOG_VERSION, "2.0.0");
        metadata.put(SERVICE_TITLE, "sTitle2");
        metadata.put(SERVICE_DESCRIPTION, "sDescription2");
        instance = getStandardInstance("service1", InstanceInfo.InstanceStatus.UP, metadata);
        APIContainer updatedContainer = service.saveContainerFromInstance("demoapp", instance);
        Calendar updatedTimestamp = updatedContainer.getLastUpdatedTimestamp();

        boolean equals = updatedTimestamp.equals(createTimestamp);
        assertFalse(equals);

        List<APIContainer> lsContainer = service.getRecentlyUpdatedContainers();
        assertEquals(1, lsContainer.size());

        assertEquals(metadata.get(CATALOG_ID), actualDemoAppContainer.getId());
        assertEquals(metadata.get(CATALOG_TITLE), actualDemoAppContainer.getTitle());
        assertEquals(metadata.get(CATALOG_DESCRIPTION), actualDemoAppContainer.getDescription());
        assertEquals(metadata.get(CATALOG_VERSION), actualDemoAppContainer.getVersion());

        Set<APIService> apiServices = updatedContainer.getServices();
        assertEquals(1, apiServices.size());

        APIService actualService = apiServices.iterator().next();
        assertEquals(instance.getAppName().toLowerCase(), actualService.getServiceId());
        assertEquals(metadata.get(SERVICE_TITLE), actualService.getTitle());
        assertEquals(metadata.get(SERVICE_DESCRIPTION), actualService.getDescription());
        assertEquals(instance.isPortEnabled(InstanceInfo.PortType.SECURE), actualService.isSecured());
        assertEquals(instance.getHomePageUrl(), actualService.getHomePageUrl());
    }

    private InstanceInfo getStandardInstance(String serviceId,
                                             InstanceInfo.InstanceStatus status,
                                             HashMap<String, String> metadata) {
        return InstanceInfo.Builder.newBuilder()
            .setInstanceId(serviceId)
            .setAppName(serviceId)
            .setStatus(status)
            .setHostName("localhost")
            .setHomePageUrl(null, "https://localhost:8080/")
            .setVIPAddress(serviceId)
            .setMetadata(metadata)
            .build();
    }

    private InstanceInfo createApp(String serviceId, String catalogId) {
        return createApp(serviceId,
            catalogId,
            InstanceInfo.InstanceStatus.UP);
    }

    private InstanceInfo createApp(String serviceId, String catalogId, InstanceInfo.InstanceStatus status) {
        return createApp(serviceId, catalogId, "Title", "Description", "1.0.0", status);
    }

    private InstanceInfo createApp(String serviceId, String catalogId, String catalogVersion, String title) {
        return createApp(serviceId, catalogId, title, "Description", catalogVersion, InstanceInfo.InstanceStatus.UP);
    }

    private InstanceInfo createApp(String serviceId,
                                   String catalogId,
                                   String catalogTitle,
                                   String catalogDescription,
                                   String catalogVersion,
                                   InstanceInfo.InstanceStatus status) {
        HashMap<String, String> metadata = new HashMap<>();
        metadata.put(CATALOG_ID, catalogId);
        metadata.put(CATALOG_TITLE, catalogTitle);
        metadata.put(CATALOG_DESCRIPTION, catalogDescription);
        metadata.put(CATALOG_VERSION, catalogVersion);

        return getStandardInstance(serviceId, status, metadata);
    }
}
