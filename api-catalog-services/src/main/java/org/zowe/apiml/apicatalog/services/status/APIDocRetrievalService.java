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

import com.netflix.appinfo.InstanceInfo;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import org.zowe.apiml.apicatalog.instance.InstanceRetrievalService;
import org.zowe.apiml.apicatalog.services.cached.model.ApiDocInfo;
import org.zowe.apiml.apicatalog.services.status.model.ApiDocNotFoundException;
import org.zowe.apiml.apicatalog.services.status.model.ApiVersionNotFoundException;
import org.zowe.apiml.apicatalog.swagger.SubstituteSwaggerGenerator;
import org.zowe.apiml.config.ApiInfo;
import org.zowe.apiml.eurekaservice.client.util.EurekaMetadataParser;
import org.zowe.apiml.product.gateway.GatewayClient;
import org.zowe.apiml.product.gateway.GatewayConfigProperties;
import org.zowe.apiml.product.routing.RoutedService;
import org.zowe.apiml.product.routing.RoutedServices;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Retrieves the API documentation for a registered service
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class APIDocRetrievalService {

    @Autowired
    @Qualifier("secureHttpClientWithoutKeystore")
    private final CloseableHttpClient secureHttpClientWithoutKeystore;

    private final InstanceRetrievalService instanceRetrievalService;
    private final GatewayClient gatewayClient;

    private final EurekaMetadataParser metadataParser = new EurekaMetadataParser();
    private final SubstituteSwaggerGenerator swaggerGenerator = new SubstituteSwaggerGenerator();

    /**
     * Retrieves the available API versions for a registered service.
     * Takes the versions available in each 'apiml.service.apiInfo' element.
     *
     * @param serviceId the unique service ID
     * @return a list of API version strings
     * @throws ApiVersionNotFoundException if the API versions cannot be loaded
     */
    public List<String> retrieveApiVersions(@NonNull String serviceId) {
        log.debug("Retrieving API versions for service '{}'", serviceId);
        InstanceInfo instanceInfo;

        try {
            instanceInfo = getInstanceInfo(serviceId);
        } catch (ApiDocNotFoundException e) {
            throw new ApiVersionNotFoundException(e.getMessage());
        }

        List<ApiInfo> apiInfoList = metadataParser.parseApiInfo(instanceInfo.getMetadata());
        List<String> apiVersions = new ArrayList<>();
        for (ApiInfo apiInfo : apiInfoList) {
            apiVersions.add(apiInfo.getApiId() + " v" + apiInfo.getVersion());
        }

        log.debug("For service '{}' found API versions '{}'", serviceId, apiVersions);
        return apiVersions;
    }

    /**
     * Retrieves the default API version for a registered service.
     * Uses 'apiml.service.apiInfo.defaultApi' field.
     * <p>
     * Returns version in the format 'v{majorVersion|'}. If no API is set as default, null is returned.
     *
     * @param serviceId the unique service ID
     * @return default API version in the format v{majorVersion}, or null.
     */
    public String retrieveDefaultApiVersion(@NonNull String serviceId) {
        log.debug("Retrieving default API version for service '{}'", serviceId);
        InstanceInfo instanceInfo;

        try {
            instanceInfo = getInstanceInfo(serviceId);
        } catch (ApiDocNotFoundException e) {
            throw new ApiVersionNotFoundException(e.getMessage());
        }

        List<ApiInfo> apiInfoList = metadataParser.parseApiInfo(instanceInfo.getMetadata());
        ApiInfo defaultApiInfo = getDefaultApiInfo(apiInfoList);

        if (defaultApiInfo == null) {
            return "";
        }

        String defaultVersion = defaultApiInfo.getApiId() + " v" + defaultApiInfo.getVersion();
        log.debug("For service '{}' found default API version '{}'", serviceId, defaultVersion);
        return defaultVersion;
    }

    /**
     * Retrieve the API docs for a registered service
     * <p>
     * API doc URL is taken from the application metadata in the following
     * order:
     * <p>
     * 1. 'apiml.service.apiInfo.swaggerUrl' (preferred way)
     * 2. 'apiml.service.apiInfo' is present and 'swaggerUrl' is not, ApiDoc info is automatically generated
     * 3. URL is constructed from 'apiml.routes.api-doc.serviceUrl'. This method is deprecated and used for
     * backwards compatibility only
     *
     * @param serviceId  the unique service id
     * @param apiVersion the version of the API
     * @return the API doc and related information for transformation
     * @throws ApiDocNotFoundException if the response is error
     */
    public ApiDocInfo retrieveApiDoc(@NonNull String serviceId, String apiVersion) {
        log.debug("Retrieving API doc for '{} {}'", serviceId, apiVersion);
        InstanceInfo instanceInfo = getInstanceInfo(serviceId);

        List<ApiInfo> apiInfoList = metadataParser.parseApiInfo(instanceInfo.getMetadata());
        ApiInfo apiInfo = findApi(apiInfoList, apiVersion);

        return buildApiDocInfo(serviceId, apiInfo, instanceInfo);
    }

    private ApiDocInfo buildApiDocInfo(String serviceId, ApiInfo apiInfo, InstanceInfo instanceInfo) {
        RoutedServices routes = metadataParser.parseRoutes(instanceInfo.getMetadata());
        String apiDocUrl = getApiDocUrl(apiInfo, instanceInfo, routes);

        if (apiDocUrl == null) {
            log.warn("No api doc URL for '{} {} {}'", serviceId, apiInfo.getApiId(), apiInfo.getVersion());
            return getApiDocInfoBySubstituteSwagger(instanceInfo, routes, apiInfo);
        }

        // The Swagger generated by zOSMF is invalid because it has null in enum values for boolean.
        // Remove once we know that zOSMF APARs for zOS2.2 and 2.3 is prerequisite
        if (serviceId.equals("zosmf") || serviceId.equals("ibmzosmf")) {
            try {
                String apiDocContent = getApiDocContentByUrl(serviceId, apiDocUrl);
                if (apiDocContent != null) {
                    apiDocContent = apiDocContent.replace("null, null", "true, false");
                }
                return new ApiDocInfo(apiInfo, apiDocContent, routes);
            } catch (Exception e) {
                log.error("Error retrieving api doc for '{}'", serviceId, e);
                return getApiDocInfoBySubstituteSwagger(instanceInfo, routes, apiInfo);
            }
        }

        String apiDocContent = "";
        try {
            apiDocContent = getApiDocContentByUrl(serviceId, apiDocUrl);
        } catch (IOException e) {
            log.error("Error retrieving api doc for '{}'", serviceId, e);
        }
        return new ApiDocInfo(apiInfo, apiDocContent, routes);
    }

    /**
     * Retrieve the default API docs for a registered service.
     * <p>
     * Default API doc is selected via the configuration parameter 'apiml.service.apiInfo.isDefault'.
     * <p>
     * If there are multiple apiInfo elements with isDefault set to 'true', or there are none set to 'true',
     * then the high API version will be selected.
     *
     * @param serviceId the unique service id
     * @return the default API doc and related information for transfer
     * @throws ApiDocNotFoundException if the response is error
     */
    public ApiDocInfo retrieveDefaultApiDoc(@NonNull String serviceId) {
        log.debug("Retrieving default API doc for service '{}'", serviceId);
        InstanceInfo instanceInfo = getInstanceInfo(serviceId);

        List<ApiInfo> apiInfoList = metadataParser.parseApiInfo(instanceInfo.getMetadata());
        ApiInfo defaultApiInfo = getDefaultApiInfo(apiInfoList);

        return buildApiDocInfo(serviceId, defaultApiInfo, instanceInfo);
    }

    private ApiInfo getDefaultApiInfo(List<ApiInfo> apiInfoList) {
        ApiInfo defaultApiInfo = getApiInfoSetAsDefault(apiInfoList);

        if (defaultApiInfo == null) {
            log.debug("No API set as default, will use highest major version as default");
            defaultApiInfo = getHighestApiVersion(apiInfoList);
        }

        return defaultApiInfo;
    }

    private ApiInfo getApiInfoSetAsDefault(List<ApiInfo> apiInfoList) {
        ApiInfo defaultApiInfo = null;
        for (ApiInfo apiInfo : apiInfoList) {
            if (apiInfo.isDefaultApi()) {
                if (defaultApiInfo != null) {
                    log.warn("Multiple API are set as default: '{} {}' and '{} {}'. Neither will be treated as the default.",
                        defaultApiInfo.getApiId(), apiInfo.getVersion(),
                        apiInfo.getApiId(), apiInfo.getVersion()
                    );
                    return null;
                } else {
                    defaultApiInfo = apiInfo;
                }
            }
        }
        return defaultApiInfo;
    }

    private ApiInfo getHighestApiVersion(List<ApiInfo> apiInfoList) {
        if (apiInfoList == null || apiInfoList.isEmpty()) {
            return null;
        }

        ApiInfo highestVersionApi = apiInfoList.get(0);
        for (ApiInfo apiInfo : apiInfoList) {
            if (isHigherVersion(apiInfo, highestVersionApi)) {
                highestVersionApi = apiInfo;
            }
        }
        return highestVersionApi;
    }

    private boolean isHigherVersion(ApiInfo toTest, ApiInfo comparedAgainst) {
        int versionToTest = getMajorVersion(toTest);
        int versionToCompare = getMajorVersion(comparedAgainst);

        return versionToTest > versionToCompare;
    }

    /**
     * Return the major version from the version field in ApiInfo.
     * <p>
     * Major version is assumed to be the first integer in the version string.
     * <p>
     * If there is no major version (that is, no integers in the version string),
     * -1 is returned as it assumed valid major versions will be 0 or higher. Thus,
     * -1 can be used in an integer comparison for highest integer.
     *
     * @param apiInfo ApiInfo for which major version will be retrieved.
     * @return int representing major version. If no version integer
     */
    private int getMajorVersion(ApiInfo apiInfo) {
        if (apiInfo == null) {
            return -1;
        }

        return apiInfo.getMajorVersion();
    }

    /**
     * Get ApiDoc url
     *
     * @param apiInfo      the apiInfo of service instance
     * @param instanceInfo the information about service instance
     * @param routes       the routes of service instance
     * @return the url of apidoc
     */
    private String getApiDocUrl(ApiInfo apiInfo, InstanceInfo instanceInfo, RoutedServices routes) {
        String apiDocUrl = null;
        if (apiInfo == null) {
            apiDocUrl = createApiDocUrlFromRouting(instanceInfo, routes);
        } else if (apiInfo.getSwaggerUrl() != null) {
            apiDocUrl = apiInfo.getSwaggerUrl();
        }

        return apiDocUrl;
    }


    /**
     * Get ApiDoc content by Url
     *
     * @param serviceId the unique service id
     * @param apiDocUrl the url of apidoc
     * @return the information about ApiDoc content as application/json
     * @throws ApiDocNotFoundException if the response is error
     */
    private String getApiDocContentByUrl(@NonNull String serviceId, String apiDocUrl) throws IOException {
        HttpGet httpGet = new HttpGet(apiDocUrl);
        httpGet.setHeader(org.apache.http.HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        String responseBody = null;
        try {
            CloseableHttpResponse response = secureHttpClientWithoutKeystore.execute(httpGet);
            final HttpEntity responseEntity = response.getEntity();
            if (responseEntity != null) {
                responseBody = EntityUtils.toString(responseEntity, StandardCharsets.UTF_8);
            }
            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                throw new ApiDocNotFoundException("No API Documentation was retrieved due to " + serviceId +
                    " server error: '" + responseBody + "'.");
            }
        } catch (Exception e) {
            log.error("Error retrieving api doc by url for {} at {}", serviceId, apiDocUrl, e);
            throw e;
        }
        return responseBody;
    }

    /**
     * Get ApiDocInfo by Substitute Swagger
     *
     * @param instanceInfo the information about service instance
     * @param routes       the routes of service instance
     * @param apiInfo      the apiInfo of service instance
     * @return the information about APIDocInfo
     */
    private ApiDocInfo getApiDocInfoBySubstituteSwagger(InstanceInfo instanceInfo,
                                                        RoutedServices routes,
                                                        ApiInfo apiInfo) {
        GatewayConfigProperties gatewayConfigProperties = gatewayClient.getGatewayConfigProperties();
        String response = swaggerGenerator.generateSubstituteSwaggerForService(
            instanceInfo,
            apiInfo,
            gatewayConfigProperties.getScheme(),
            gatewayConfigProperties.getHostname());
        return new ApiDocInfo(apiInfo, response, routes);
    }

    /**
     * Find ApiInfo for the corresponding version, if not found the first one is returned
     *
     * @param apiInfos   the list of APIs information
     * @param apiVersion the version to be find
     * @return the information about API
     */
    private ApiInfo findApi(List<ApiInfo> apiInfos, String apiVersion) {
        if (apiInfos.isEmpty()) {
            return null;
        }

        if (apiVersion == null) {
            return apiInfos.get(0);
        }

        String[] api = apiVersion.split(" ");
        String apiId = api.length > 0 ? api[0] : "";
        String version = api.length > 1 ? api[1].replace("v", "") : "";

        Optional<ApiInfo> result = apiInfos.stream()
            .filter(
                f -> apiId.equals(f.getApiId()) && (version == null || version.equals(f.getVersion()))
            )
            .findFirst();

        if (!result.isPresent()) {
            String errMessage = String.format("Error finding api doc: there is no api doc for '%s %s'.", apiId, version);
            log.error(errMessage);
            throw new ApiDocNotFoundException(errMessage);
        } else {
            return result.get();
        }
    }

    private InstanceInfo getInstanceInfo(String serviceId) {
        InstanceInfo instanceInfo = instanceRetrievalService.getInstanceInfo(serviceId);
        if (instanceInfo == null) {
            throw new ApiDocNotFoundException("Could not load instance information for service " + serviceId + ".");
        }
        return instanceInfo;
    }

    /**
     * Creates a URL from the routing metadata 'apiml.routes.api-doc.serviceUrl' when 'apiml.apiInfo.swaggerUrl' is
     * not present
     *
     * @param instanceInfo the information about service instance
     * @return the URL of API doc endpoint
     * @deprecated Added to support services which were on-boarded before 'apiml.apiInfo.swaggerUrl' parameter was
     * introduced. It will be removed when all services will be using the new configuration style.
     */
    @Deprecated
    private String createApiDocUrlFromRouting(InstanceInfo instanceInfo, RoutedServices routes) {
        String scheme;
        int port;
        if (instanceInfo.isPortEnabled(InstanceInfo.PortType.SECURE)) {
            scheme = "https";
            port = instanceInfo.getSecurePort();
        } else {
            scheme = "http";
            port = instanceInfo.getPort();
        }

        String path = null;
        RoutedService route = routes.findServiceByGatewayUrl("api/v1/api-doc");
        if (route != null) {
            path = route.getServiceUrl();
        }

        if (path == null) {
            throw new ApiDocNotFoundException("No API Documentation defined for service " + instanceInfo.getAppName().toLowerCase() + ".");
        }

        UriComponents uri = UriComponentsBuilder
            .newInstance()
            .scheme(scheme)
            .host(instanceInfo.getHostName())
            .port(port)
            .path(path)
            .build();

        return uri.toUriString();
    }
}
