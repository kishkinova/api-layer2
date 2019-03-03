/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package com.ca.mfaas.discovery.staticdef;

import com.ca.mfaas.product.model.ApiInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.LeaseInfo;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ServiceDefinitionProcessor {

    private static final String STATIC_INSTANCE_ID_PREFIX = "STATIC-";

    private static final DataCenterInfo DEFAULT_INFO = () -> DataCenterInfo.Name.MyOwn;

    private static final String DEFAULT_TILE_VERSION = "1.0.0";

    @Data
    class ProcessServicesDataResult {
        private final List<String> errors;
        private final List<InstanceInfo> instances;
    }

    public List<InstanceInfo> findServices(String staticApiDefinitionsDirectory) {
        List<InstanceInfo> instances = new ArrayList<>();

        if ((staticApiDefinitionsDirectory != null) && !staticApiDefinitionsDirectory.isEmpty()) {
            File directory = new File(staticApiDefinitionsDirectory);
            if (directory.isDirectory()) {
                instances.addAll(findServicesInDirectory(directory));
            } else {
                log.error("Directory {} is not a directory or does not exist", staticApiDefinitionsDirectory);
            }
        } else {
            log.info("No static definition directory defined");
        }

        return instances;
    }

    List<InstanceInfo> findServicesInDirectory(File directory) {
        log.info("Scanning directory with static services definition: " + directory);

        File[] ymlFiles = directory.listFiles((dir, name) -> name.endsWith(".yml"));
        List<String> yamlSourceName = new ArrayList<>();
        List<String> yamlData = new ArrayList<>();

        if (ymlFiles == null) {
            log.error("I/O problem occurred during reading directory: {}", directory.getAbsolutePath());
            ymlFiles = new File[0];
        } else if (ymlFiles.length == 0) {
            log.info("No static service definition found in directory: {}", directory.getAbsolutePath());
        }

        for (File file : ymlFiles) {
            log.info("Static API definition file: {}", file.getAbsolutePath());
            try {
                yamlSourceName.add(file.getAbsolutePath());
                yamlData.add(new String(Files.readAllBytes(Paths.get(file.getAbsolutePath()))));
            } catch (IOException e) {
                log.error("Error loading file {}", file.getAbsolutePath(), e);
            }
        }
        ProcessServicesDataResult result = processServicesData(yamlSourceName, yamlData);
        for (String error : result.getErrors()) {
            log.error(error);
        }
        return result.getInstances();
    }

    ProcessServicesDataResult processServicesData(List<String> yamlSourceNames, List<String> yamlStringList) {
        List<String> errors = new ArrayList<>();
        List<Service> services = new ArrayList<>();
        Map<String, CatalogUiTile> tiles = new HashMap<>();
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        int i = 0;
        for (String yamlString : yamlStringList) {
            try {
                Definition def = mapper.readValue(yamlString, Definition.class);
                services.addAll(def.getServices());
                if (def.getCatalogUiTiles() != null) {
                    tiles.putAll(def.getCatalogUiTiles());
                }
            } catch (IOException e) {
                errors.add(String.format("Error processing file %s - %s", yamlSourceNames.get(i), e.getMessage()));
            }
            i++;
        }
        ProcessServicesDataResult result = createInstances(services, tiles);
        errors.addAll(result.getErrors());
        return new ProcessServicesDataResult(errors, result.getInstances());
    }

    private ProcessServicesDataResult createInstances(List<Service> services, Map<String, CatalogUiTile> tiles) {
        List<InstanceInfo> instances = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (Service service : services) {
            try {
                String serviceId = service.getServiceId();

                CatalogUiTile tile = null;
                if (service.getCatalogUiTileId() != null) {
                    tile = tiles.get(service.getCatalogUiTileId());
                    if (tile == null) {
                        errors.add(String.format("The API Catalog UI tile ID %s is invalid. The service %s will not have API Catalog UI tile", service.getCatalogUiTileId(), serviceId));
                    } else {
                        tile.setId(service.getCatalogUiTileId());
                    }
                }

                for (String instanceBaseUrl : service.getInstanceBaseUrls()) {
                    try {
                        URL url = new URL(instanceBaseUrl);
                        if (url.getHost().isEmpty()) {
                            errors.add(String.format("The URL %s does not contain a hostname. The instance of %s will not be created", instanceBaseUrl, service.getServiceId()));
                        } else if (url.getPort() == -1) {
                            errors.add(String.format("The URL %s does not contain a port number. The instance of %s will not be created", instanceBaseUrl, service.getServiceId()));
                        } else {
                            InstanceInfo.Builder builder = InstanceInfo.Builder.newBuilder();
                            String instanceId = String.format("%s%s:%s:%s", STATIC_INSTANCE_ID_PREFIX, url.getHost(), serviceId, url.getPort());
                            String ipAddress = InetAddress.getByName(url.getHost()).getHostAddress();
                            setInstanceAttributes(builder, service, serviceId, instanceId, instanceBaseUrl, url, ipAddress, tile);
                            setPort(builder, service, instanceBaseUrl, url);
                            log.info("Adding static instance {} for service ID {} mapped to URL {}", instanceId, serviceId,
                                url);
                            instances.add(builder.build());
                        }
                    } catch (MalformedURLException e) {
                        errors.add(String.format("The URL %s is malformed. The instance will not be created: %s",
                            instanceBaseUrl, e.getMessage()));
                    } catch (UnknownHostException e) {
                        errors.add(String.format("The hostname of URL %s is unknown. The instance will not be created: %s",
                            instanceBaseUrl, e.getMessage()));
                    }
                }

            } catch (NullPointerException e) {
                errors.add(String.format("The instanceBaseUrl of %s is not defined. The instance will not be created: %s", service.getServiceId(), e.getMessage()));
            }
        }
        return new ProcessServicesDataResult(errors, instances);
    }

    private void setInstanceAttributes(InstanceInfo.Builder builder,Service service, String serviceId,
                                       String instanceId, String instanceBaseUrl, URL url, String ipAddress, CatalogUiTile tile) {
        builder.setAppName(serviceId).setInstanceId(instanceId).setHostName(url.getHost()).setIPAddr(ipAddress)
            .setDataCenterInfo(DEFAULT_INFO).setVIPAddress(serviceId).setSecureVIPAddress(serviceId)
            .setLeaseInfo(LeaseInfo.Builder.newBuilder()
                .setRenewalIntervalInSecs(LeaseInfo.DEFAULT_LEASE_RENEWAL_INTERVAL)
                .setDurationInSecs(LeaseInfo.DEFAULT_LEASE_DURATION).build())
            .setMetadata(createMetadata(service, url, tile));

        if (service.getHomePageRelativeUrl() != null) {
            builder.setHomePageUrl(null, instanceBaseUrl + service.getHomePageRelativeUrl());
        }

        if (service.getStatusPageRelativeUrl() != null) {
            builder.setStatusPageUrl(null, instanceBaseUrl + service.getStatusPageRelativeUrl());
        }
    }

    private void setPort(InstanceInfo.Builder builder, Service service, String instanceBaseUrl, URL url) throws MalformedURLException {
        switch (url.getProtocol()) {
            case "http":
                builder.enablePort(InstanceInfo.PortType.SECURE, false).enablePort(InstanceInfo.PortType.UNSECURE, true)
                    .setPort(url.getPort()).setSecurePort(url.getPort());
                if (service.getHealthCheckRelativeUrl() != null) {
                    builder.setHealthCheckUrls(null, instanceBaseUrl + service.getHealthCheckRelativeUrl(), null);
                }
                break;
            case "https":
                builder.enablePort(InstanceInfo.PortType.SECURE, true).enablePort(InstanceInfo.PortType.UNSECURE, false)
                    .setSecurePort(url.getPort()).setPort(url.getPort());
                if (service.getHealthCheckRelativeUrl() != null) {
                    builder.setHealthCheckUrls(null, null, instanceBaseUrl + service.getHealthCheckRelativeUrl());
                }
                break;
            default:
                throw new MalformedURLException("Invalid protocol");
        }
    }

    private Map<String, String> createMetadata(Service service, URL url, CatalogUiTile tile) {
        Map<String, String> mt = new HashMap<>();

        mt.put("mfaas.discovery.service.title", service.getTitle());
        mt.put("mfaas.discovery.service.description", service.getDescription());

        if (service.getRoutes() != null) {
            for (Route rs : service.getRoutes()) {
                String gatewayUrl = UrlUtils.trimSlashes(rs.getGatewayUrl());
                String key = gatewayUrl.replace("/", "-");
                String serviceUrl = url.getPath()
                    + (rs.getServiceRelativeUrl() == null ? "" : rs.getServiceRelativeUrl());
                mt.put(String.format("routed-services.%s.gateway-url", key), gatewayUrl);
                mt.put(String.format("routed-services.%s.service-url", key), serviceUrl);
            }
        }

        if (tile != null) {
            mt.put("mfaas.discovery.catalogUiTile.id", tile.getId());
            mt.put("mfaas.discovery.catalogUiTile.version", DEFAULT_TILE_VERSION);
            mt.put("mfaas.discovery.catalogUiTile.title", tile.getTitle());
            mt.put("mfaas.discovery.catalogUiTile.description", tile.getDescription());

            if (service.getApiInfo() != null) {
                int i = 0;
                for (ApiInfo apiInfo: service.getApiInfo()) {
                    i++;

                    mt.put(String.format("apiml.apiInfo.%d.gatewayUrl", i), apiInfo.getGatewayUrl());
                    mt.put(String.format("apiml.apiInfo.%d.version", i), apiInfo.getVersion());

                    if (apiInfo.getSwaggerUrl() != null) {
                        try {
                            new URL(apiInfo.getSwaggerUrl());
                        } catch (MalformedURLException e) {
                            throw new InvalidParameterException(
                                String.format("The Swagger URL \"%s\" for service %s is not valid: %s",
                                service.getServiceId(), apiInfo.getSwaggerUrl(), e.getMessage()));
                        }
                        mt.put(String.format("apiml.apiInfo.%d.swaggerUrl", i), apiInfo.getSwaggerUrl());
                    }

                    if (apiInfo.getDocumentationUrl() != null) {
                        try {
                            new URL(apiInfo.getDocumentationUrl());
                        } catch (MalformedURLException e) {
                            throw new InvalidParameterException(
                                String.format("The documentation URL \"%s\" for service %s is not valid: %s",
                                service.getServiceId(), apiInfo.getDocumentationUrl(), e.getMessage()));
                        }
                        mt.put(String.format("apiml.apiInfo.%d.documentationUrl", i), apiInfo.getDocumentationUrl());
                    }
                }
            }
        }
        else {
            mt.put("mfaas.discovery.enableApiDoc", "false");
        }

        return mt;
    }
}
