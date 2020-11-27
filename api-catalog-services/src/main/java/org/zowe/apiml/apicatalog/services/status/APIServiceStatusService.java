/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.apicatalog.services.status;

import lombok.AllArgsConstructor;
import org.openapitools.openapidiff.core.model.ChangedOpenApi;
import org.openapitools.openapidiff.core.output.HtmlRender;
import org.zowe.apiml.apicatalog.model.APIContainer;
import org.zowe.apiml.apicatalog.services.cached.CachedApiDocService;
import org.zowe.apiml.apicatalog.services.cached.CachedProductFamilyService;
import org.zowe.apiml.apicatalog.services.cached.CachedServicesService;
import org.zowe.apiml.apicatalog.services.status.event.model.ContainerStatusChangeEvent;
import org.zowe.apiml.apicatalog.services.status.event.model.STATUS_EVENT_TYPE;
import org.zowe.apiml.apicatalog.services.status.model.ApiDiffNotAvailableException;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Applications;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class APIServiceStatusService {

    private final CachedProductFamilyService cachedProductFamilyService;
    private final CachedServicesService cachedServicesService;
    private final CachedApiDocService cachedApiDocService;
    private final OpenApiCompareProducer openApiCompareProducer;

    /**
     * Return a cached snapshot of services and instances as a response
     *
     * @return Applications from cache
     */
    public ResponseEntity<Applications> getCachedApplicationStateResponse() {
        return new ResponseEntity<>(cachedServicesService.getAllCachedServices(), createHeaders(), HttpStatus.OK);
    }

    /**
     * Return a cached snapshot of services and instances
     *
     * @return Applications from cache
     */
    public Applications getCachedApplicationState() {
        return cachedServicesService.getAllCachedServices();
    }

    /**
     * Retrieve all containers and return them as events
     *
     * @return container status as events
     */
    public List<ContainerStatusChangeEvent> getContainersStateAsEvents() {
        log.debug("Retrieving all containers statuses as events");
        List<ContainerStatusChangeEvent> events = new ArrayList<>();
        Iterable<APIContainer> allContainers = cachedProductFamilyService.getAllContainers();
        allContainers.forEach(container -> {
            cachedProductFamilyService.calculateContainerServiceTotals(container);
            addContainerEvent(events, container);
        });
        return events;
    }

    /**
     * Return the cached API docs for a service
     *
     * @param serviceId  the unique service id
     * @param apiVersion the version of the API
     * @return a version of an API Doc
     */
    public ResponseEntity<String> getServiceCachedApiDocInfo(@NonNull String serviceId, String apiVersion) {
        return new ResponseEntity<>(cachedApiDocService.getApiDocForService(serviceId, apiVersion), createHeaders(), HttpStatus.OK);
    }

    /**
     * Return the diff of two api versions
     * @param serviceId the unique service id
     * @param apiVersion1 the old version of the api
     * @param apiVersion2 the new version of the api
     * @return response containing HTML document detailing changes between api doc versions
     */
    public ResponseEntity<String> getApiDiffInfo(@NonNull String serviceId, String apiVersion1, String apiVersion2) {
        String doc1 = cachedApiDocService.getApiDocForService(serviceId, apiVersion1);
        String doc2 = cachedApiDocService.getApiDocForService(serviceId, apiVersion2);
        try {
            ChangedOpenApi diff = openApiCompareProducer.fromContents(doc1, doc2);
            HtmlRender render = new HtmlRender();
            String result = render.render(diff);
            //Remove external stylesheet
            result = result.replace("<link rel=\"stylesheet\" href=\"http://deepoove.com/swagger-diff/stylesheets/demo.css\">", "");
            return new ResponseEntity<>(result, createHeaders(), HttpStatus.OK);
        } catch (NullPointerException e) {
            throw new ApiDiffNotAvailableException(String.format("No Diff available for %s and versions %s and %s", serviceId, apiVersion1, apiVersion2));
        }
    }

    /**
     * Retrieve all containers which were updated inside a given threshold value and return them as events
     *
     * @return recent container status as events
     */
    public List<ContainerStatusChangeEvent> getRecentlyUpdatedContainersAsEvents() {
        List<ContainerStatusChangeEvent> recentEvents = new ArrayList<>();
        Iterable<APIContainer> allContainers = cachedProductFamilyService.getRecentlyUpdatedContainers();
        allContainers.forEach(container -> {
            cachedProductFamilyService.calculateContainerServiceTotals(container);
            addContainerEvent(recentEvents, container);
        });
        if (!recentEvents.isEmpty()) {
            log.debug("Recent events found: " + recentEvents.size());
        }
        return recentEvents;
    }

    /**
     * Create an event based on the status of the instance
     *
     * @param events    the list of events to return
     * @param container the instance
     */
    private void addContainerEvent(List<ContainerStatusChangeEvent> events, APIContainer container) {
        STATUS_EVENT_TYPE eventType;
        if (InstanceInfo.InstanceStatus.DOWN.name().equalsIgnoreCase(container.getStatus())) {
            eventType = STATUS_EVENT_TYPE.CANCEL;
        } else if (container.getCreatedTimestamp().equals(container.getLastUpdatedTimestamp())) {
            eventType = STATUS_EVENT_TYPE.CREATED_CONTAINER;
        } else {
            eventType = STATUS_EVENT_TYPE.RENEW;
        }
        events.add(new ContainerStatusChangeEvent(
            container.getId(),
            container.getTitle(),
            container.getStatus(),
            container.getTotalServices(),
            container.getActiveServices(),
            container.getServices(),
            eventType)
        );
    }

// ============================== HELPER METHODS


    /**
     * HTTP headers
     *
     * @return headers for requests
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON_UTF8));
        return headers;
    }
}
