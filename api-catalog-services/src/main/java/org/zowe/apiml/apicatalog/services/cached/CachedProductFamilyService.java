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
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.zowe.apiml.apicatalog.model.APIContainer;
import org.zowe.apiml.apicatalog.model.APIService;
import org.zowe.apiml.apicatalog.model.SemanticVersion;
import org.zowe.apiml.auth.Authentication;
import org.zowe.apiml.auth.AuthenticationSchemes;
import org.zowe.apiml.config.ApiInfo;
import org.zowe.apiml.eurekaservice.client.util.EurekaMetadataParser;
import org.zowe.apiml.message.log.ApimlLogger;
import org.zowe.apiml.product.constants.CoreService;
import org.zowe.apiml.product.logging.annotations.InjectApimlLogger;
import org.zowe.apiml.product.routing.RoutedServices;
import org.zowe.apiml.product.routing.ServiceType;
import org.zowe.apiml.product.routing.transform.TransformService;
import org.zowe.apiml.product.routing.transform.URLTransformationException;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.zowe.apiml.constants.EurekaMetadataDefinition.*;

/**
 * Caching service for eureka services
 */
@Slf4j
@Service
@CacheConfig(cacheNames = {"products"})
public class CachedProductFamilyService {

    @InjectApimlLogger
    private final ApimlLogger apimlLog = ApimlLogger.empty();

    private final Integer cacheRefreshUpdateThresholdInMillis;

    private final CachedServicesService cachedServicesService;
    private final EurekaMetadataParser metadataParser = new EurekaMetadataParser();
    private final TransformService transformService;

    private final Map<String, APIContainer> products = new HashMap<>();

    private final AuthenticationSchemes schemes = new AuthenticationSchemes();

    public CachedProductFamilyService(CachedServicesService cachedServicesService,
                                      TransformService transformService,
                                      @Value("${apiml.service-registry.cacheRefreshUpdateThresholdInMillis}")
                                          Integer cacheRefreshUpdateThresholdInMillis) {
        this.cachedServicesService = cachedServicesService;
        this.transformService = transformService;
        this.cacheRefreshUpdateThresholdInMillis = cacheRefreshUpdateThresholdInMillis;
    }

    /**
     * Return all cached service instances
     *
     * @return instances
     */
    @Cacheable
    public Collection<APIContainer> getAllContainers() {
        return products.values();
    }


    /**
     * return cached service instance by id
     *
     * @param id service identifier
     * @return {@link APIContainer}
     */
    public APIContainer getContainerById(String id) {
        return products.get(id);
    }

    /**
     * Retrieve any containers which have had their details updated after the threshold figure
     * If performance is slow then possibly cache the result and evict after 'n' seconds
     *
     * @return recently updated containers
     */
    public List<APIContainer> getRecentlyUpdatedContainers() {
        return this.products.values().stream().filter(
            container -> {
                boolean isRecent = container.isRecentUpdated(cacheRefreshUpdateThresholdInMillis);
                if (isRecent) {
                    log.debug("Container: " + container.getId() + " last updated: "
                        + container.getLastUpdatedTimestamp().getTime() +
                        " was updated recently");
                }
                return isRecent;
            }).collect(toList());
    }

    /**
     * Add service to container
     *
     * @param productFamilyId the service identifier
     * @param instanceInfo    InstanceInfo
     */
    @CachePut(key = "#productFamilyId")
    public void addServiceToContainer(final String productFamilyId, final InstanceInfo instanceInfo) {
        APIContainer apiContainer = products.get(productFamilyId);
        // fix - throw error if null
        apiContainer.addService(createAPIServiceFromInstance(instanceInfo));
        products.put(productFamilyId, apiContainer);
    }

    /**
     * Save a containers details using a service's metadata
     *
     * @param productFamilyId the product family id of the container
     * @param instanceInfo    the service instance
     */
    @CachePut(key = "#productFamilyId")
    public APIContainer saveContainerFromInstance(String productFamilyId, InstanceInfo instanceInfo) {
        APIContainer container = products.get(productFamilyId);
        if (container == null) {
            container = createNewContainerFromService(productFamilyId, instanceInfo);
        } else {
            Set<APIService> apiServices = container.getServices();
            APIService service = createAPIServiceFromInstance(instanceInfo);
            apiServices.remove(service);

            apiServices.add(service);
            container.setServices(apiServices);
            //update container
            String versionFromInstance = instanceInfo.getMetadata().get(CATALOG_VERSION);
            String title = instanceInfo.getMetadata().get(CATALOG_TITLE);
            String description = instanceInfo.getMetadata().get(CATALOG_DESCRIPTION);

            container.setVersion(versionFromInstance);
            container.setTitle(title);
            container.setDescription(description);
            container.updateLastUpdatedTimestamp();

            products.put(productFamilyId, container);
        }

        return container;
    }

    /**
     * Update the summary totals, sso and API IDs info for a container based on it's running services
     *
     * @param apiContainer calculate totals for this container
     */
    public void calculateContainerServiceValues(APIContainer apiContainer) {
        if (apiContainer.getServices() == null) {
            apiContainer.setServices(new HashSet<>());
        }

        int servicesCount = apiContainer.getServices().size();
        int activeServicesCount = 0;
        boolean isSso = servicesCount > 0;
        for (APIService apiService : apiContainer.getServices()) {
            if (update(apiService)) {
                activeServicesCount ++;
            }
            isSso &= apiService.isSsoAllInstances();
        }

        setStatus(apiContainer, servicesCount, activeServicesCount);
        apiContainer.setSso(isSso);
    }

    /**
     * Return the number of containers (used for checking if a new container was created)
     *
     * @return the number of containers
     */
    public int getContainerCount() {
        return products.size();
    }

    /**
     * Try to transform the service homepage url and return it. If it fails,
     * return the original homepage url
     *
     * @param instanceInfo the service instance
     * @return the transformed homepage url
     */
    private String getInstanceHomePageUrl(InstanceInfo instanceInfo) {
        String instanceHomePage = instanceInfo.getHomePageUrl();

        if (hasHomePage(instanceInfo)) {
            instanceHomePage = instanceHomePage.trim();
            RoutedServices routes = metadataParser.parseRoutes(instanceInfo.getMetadata());

            try {
                instanceHomePage = transformService.transformURL(
                    ServiceType.UI,
                    instanceInfo.getVIPAddress(),
                    instanceHomePage,
                    routes);
            } catch (URLTransformationException | IllegalArgumentException e) {
                apimlLog.log("org.zowe.apiml.apicatalog.homePageTransformFailed", instanceInfo.getAppName(), e.getMessage());
            }
        }

        log.debug("Homepage URL for {} service is: {}", instanceInfo.getVIPAddress(), instanceHomePage);
        return instanceHomePage;
    }

    /**
     * Get the base path for the service.
     *
     * @param instanceInfo the service instance
     * @return the base URL
     */
    private String getApiBasePath(InstanceInfo instanceInfo) {
        String apiBasePath = "";
        if (hasHomePage(instanceInfo)) {
            try {
                RoutedServices routes = metadataParser.parseRoutes(instanceInfo.getMetadata());
                apiBasePath = transformService.retrieveApiBasePath(
                    instanceInfo.getVIPAddress(),
                    instanceInfo.getHomePageUrl(),
                    routes);
            } catch (URLTransformationException e) {
                apimlLog.log("org.zowe.apiml.apicatalog.getApiBasePathFailed", instanceInfo.getAppName(), e.getMessage());
            }
        }
        return apiBasePath;
    }

    private boolean hasHomePage(InstanceInfo instanceInfo) {
        String instanceHomePage = instanceInfo.getHomePageUrl();
        return instanceHomePage != null
            && !instanceHomePage.isEmpty()
            //Gateway homePage is used to hold DVIPA address and must not be modified
            && !instanceInfo.getAppName().equalsIgnoreCase(CoreService.GATEWAY.getServiceId());
    }

    /**
     * Create a new container based on information in a new instance
     *
     * @param productFamilyId parent id
     * @param instanceInfo    instance
     * @return a new container
     */
    private APIContainer createNewContainerFromService(String productFamilyId, InstanceInfo instanceInfo) {
        Map<String, String> instanceInfoMetadata = instanceInfo.getMetadata();
        String title = instanceInfoMetadata.get(CATALOG_TITLE);
        String description = instanceInfoMetadata.get(CATALOG_DESCRIPTION);
        String version = instanceInfoMetadata.get(CATALOG_VERSION);
        APIContainer container = new APIContainer();
        container.setStatus("UP");
        container.setId(productFamilyId);
        container.setDescription(description);
        container.setTitle(title);
        container.setVersion(version);
        log.debug("updated Container cache with product family: " + productFamilyId + ": " + title);

        // create API Service from instance and update container last changed date
        container.addService(createAPIServiceFromInstance(instanceInfo));
        products.put(productFamilyId, container);
        return container;
    }

    /**
     * Create a APIService object using the instances metadata
     *
     * @param instanceInfo the service instance
     * @return a APIService object
     */
    private APIService createAPIServiceFromInstance(InstanceInfo instanceInfo) {
        boolean secureEnabled = instanceInfo.isPortEnabled(InstanceInfo.PortType.SECURE);

        String instanceHomePage = getInstanceHomePageUrl(instanceInfo);
        String apiBasePath = getApiBasePath(instanceInfo);
        Map<String, String> apiId = new HashMap<>();
        Map<String, String> gatewayUrls = new HashMap<>();
        try {
            apiId = metadataParser.parseApiInfo(instanceInfo.getMetadata()).stream().filter(apiInfo -> apiInfo.getApiId() != null).collect(
                Collectors.toMap(
                    apiInfo -> (apiInfo.getMajorVersion() < 0) ? "default" : apiInfo.getApiId() + " v" + apiInfo.getVersion(),
                    ApiInfo::getApiId
                )
            );

            gatewayUrls = metadataParser.parseApiInfo(instanceInfo.getMetadata()).stream().filter(apiInfo -> apiInfo.getApiId() != null).collect(
                Collectors.toMap(
                    apiInfo -> (apiInfo.getMajorVersion() < 0) ? "default" : apiInfo.getApiId() + " v" + apiInfo.getVersion(),
                    ApiInfo::getGatewayUrl
                )
            );
        } catch (Exception ex) {
            log.info("createApiServiceFromInstance#incorrectVersions {}", ex.getMessage());
        }

        return new APIService.Builder(instanceInfo.getAppName().toLowerCase())
            .title(instanceInfo.getMetadata().get(SERVICE_TITLE))
            .description(instanceInfo.getMetadata().get(SERVICE_DESCRIPTION))
            .secured(secureEnabled)
            .baseUrl(instanceInfo.getHomePageUrl())
            .homePageUrl(instanceHomePage)
            .basePath(apiBasePath)
            .sso(isSso(instanceInfo))
            .apiId(apiId)
            .gatewayUrls(gatewayUrls)
            .build();
    }

    private boolean isSso(InstanceInfo instanceInfo) {
        Map<String, String> eurekaMetadata = instanceInfo.getMetadata();
        return Authentication.builder()
            .scheme(schemes.map(eurekaMetadata.get(AUTHENTICATION_SCHEME)))
            .supportsSso(BooleanUtils.toBooleanObject(eurekaMetadata.get(AUTHENTICATION_SSO)))
            .build()
            .supportsSso();
    }

    private boolean update(APIService apiService) {
        Application application = cachedServicesService.getService(apiService.getServiceId());
        // service has not cached yet, but count as alive
        if (application == null) return true;

        List<InstanceInfo> instancies = application.getInstances();
        boolean isUp = instancies.stream().anyMatch(i -> InstanceInfo.InstanceStatus.UP.equals(i.getStatus()));
        boolean isSso = instancies.stream().allMatch(this::isSso);

        apiService.setStatus(isUp ? "UP" : "DOWN");
        apiService.setSsoAllInstances(isSso);

        return isUp;
    }

    private void setStatus(APIContainer apiContainer, int servicesCount, int activeServicesCount) {
        apiContainer.setTotalServices(servicesCount);
        apiContainer.setActiveServices(activeServicesCount);

        if (activeServicesCount == 0) {
            apiContainer.setStatus("DOWN");
        } else if (activeServicesCount == servicesCount) {
            apiContainer.setStatus("UP");
        } else {
            apiContainer.setStatus("WARNING");
        }
    }

}
