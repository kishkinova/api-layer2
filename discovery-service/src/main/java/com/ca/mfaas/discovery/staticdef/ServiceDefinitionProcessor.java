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

import com.ca.mfaas.config.ApiInfo;
import com.ca.mfaas.eurekaservice.client.util.EurekaMetadataParser;
import com.ca.mfaas.exception.MetadataValidationException;
import com.ca.mfaas.exception.ServiceDefinitionException;
import com.ca.mfaas.util.UrlUtils;
import com.ca.mfaas.message.log.ApimlLogger;
import com.ca.mfaas.product.logging.annotations.InjectApimlLogger;
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
import java.util.*;

import static com.ca.mfaas.constants.EurekaMetadataDefinition.*;

/**
 * Processes static definition files and creates service instances
 */
@Slf4j
@Component
public class ServiceDefinitionProcessor {

    @InjectApimlLogger
    private ApimlLogger apimlLog = ApimlLogger.empty();

    private static final String STATIC_INSTANCE_ID_PREFIX = "STATIC-";

    private static final DataCenterInfo DEFAULT_INFO = () -> DataCenterInfo.Name.MyOwn;

    private static final String DEFAULT_TILE_VERSION = "1.0.0";

    @Data
    static class ProcessServicesDataResult {
        private final List<String> errors;
        private final List<InstanceInfo> instances;
    }

    /**
     * Creates a list of instances from static definition files
     *
     * @param staticApiDefinitionsDirectories directories containing static definitions
     * @return list of instances
     */
    public List<InstanceInfo> findServices(String staticApiDefinitionsDirectories) {
        List<InstanceInfo> instances = new ArrayList<>();

        if (staticApiDefinitionsDirectories != null && !staticApiDefinitionsDirectories.isEmpty()) {
            String[] directories = staticApiDefinitionsDirectories.split(";");
            Arrays.stream(directories)
                .filter(s -> !s.isEmpty())
                .map(File::new)
                .forEach(directory -> {
                    if (directory.isDirectory()) {
                        log.debug("Found directory {}", directory.getPath());
                        instances.addAll(findServicesInDirectory(directory));
                    } else {
                        apimlLog.log("apiml.discovery.staticDefinitionsDirectoryNotValid", directory.getPath());
                    }
                });
        } else {
            log.info("No static definition directory defined");
        }

        return instances;
    }

    private List<InstanceInfo> findServicesInDirectory(File directory) {
        log.info("Scanning directory with static services definition: " + directory);

        File[] ymlFiles = directory.listFiles((dir, name) -> name.endsWith(".yml"));
        Map<String, String> ymlSources = new HashMap<>();

        if (ymlFiles == null) {
            apimlLog.log("apiml.discovery.errorReadingStaticDefinitionFolder", directory.getAbsolutePath());
            ymlFiles = new File[0];
        } else if (ymlFiles.length == 0) {
            log.info("No static service definition found in directory: {}", directory.getAbsolutePath());
        }

        for (File file : ymlFiles) {
            log.info("Static API definition file: {}", file.getAbsolutePath());
            try {
                ymlSources.put(file.getAbsolutePath(), new String(Files.readAllBytes(Paths.get(file.getAbsolutePath()))));
            } catch (IOException e) {
                apimlLog.log("apiml.discovery.errorParsingStaticDefinitionFile", file.getAbsolutePath());
            }
        }
        ProcessServicesDataResult result = processServicesData(ymlSources);
        apimlLog.log("apiml.discovery.errorParsingStaticDefinitionData", result.getErrors());
        return result.getInstances();
    }

    ProcessServicesDataResult processServicesData(Map<String, String> ymlSources) {
        List<String> errors = new ArrayList<>();
        List<InstanceInfo> instances = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        for (Map.Entry<String, String> ymlSource : ymlSources.entrySet()) {
            processServiceDefinition(ymlSource.getKey(), ymlSource.getValue(), mapper, errors, instances);
        }
        return new ProcessServicesDataResult(errors, instances);
    }

    private void processServiceDefinition(String ymlFileName, String ymlData,
                                          ObjectMapper mapper,
                                          List<String> errors,
                                          List<InstanceInfo> instances) {
        List<Service> services = new ArrayList<>();
        Map<String, CatalogUiTile> tiles = new HashMap<>();
        try {
            Definition def = mapper.readValue(ymlData, Definition.class);
            services.addAll(def.getServices());
            if (def.getCatalogUiTiles() != null) {
                tiles.putAll(def.getCatalogUiTiles());
            }
        } catch (IOException e) {
            errors.add(String.format("Error processing file %s - %s", ymlFileName, e.getMessage()));
        }
        ProcessServicesDataResult result = createInstances(ymlFileName, services, tiles);
        errors.addAll(result.getErrors());
        instances.addAll(result.getInstances());
    }

    private ProcessServicesDataResult createInstances(String ymlFileName,
                                                      List<Service> services,
                                                      Map<String, CatalogUiTile> tiles) {
        List<InstanceInfo> instances = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (Service service : services) {
            try {
                String serviceId = service.getServiceId();
                if (serviceId == null) {
                    throw new ServiceDefinitionException(String.format("ServiceId is not defined in the file '%s'. The instance will not be created.", ymlFileName));
                }

                if (service.getInstanceBaseUrls() == null) {
                    throw new ServiceDefinitionException(String.format("The instanceBaseUrls parameter of %s is not defined. The instance will not be created.", service.getServiceId()));
                }

                CatalogUiTile tile = null;
                if (service.getCatalogUiTileId() != null) {
                    tile = tiles.get(service.getCatalogUiTileId());
                        if (tile == null) {
                        errors.add(String.format("Error processing file %s - The API Catalog UI tile ID %s is invalid. The service %s will not have API Catalog UI tile", ymlFileName, service.getCatalogUiTileId(), serviceId));
                    } else {
                        tile.setId(service.getCatalogUiTileId());
                    }
                }

                for (String instanceBaseUrl : service.getInstanceBaseUrls()) {
                    buildInstanceInfo(instances, errors, service, tile, instanceBaseUrl);
                }
            } catch (ServiceDefinitionException e) {
                errors.add(e.getMessage());
            }
        }

        return new ProcessServicesDataResult(errors, instances);
    }

    private void buildInstanceInfo(List<InstanceInfo> instances,
                                   List<String> errors, Service service,
                                   CatalogUiTile tile, String instanceBaseUrl) throws ServiceDefinitionException {
        if (instanceBaseUrl == null || instanceBaseUrl.isEmpty()) {
            throw new ServiceDefinitionException(String.format("One of the instanceBaseUrl of %s is not defined. The instance will not be created.", service.getServiceId()));
        }

        String serviceId = service.getServiceId();
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

                setInstanceAttributes(builder, service, instanceId, instanceBaseUrl, url, ipAddress, tile);

                setPort(builder, service, instanceBaseUrl, url);
                log.info("Adding static instance {} for service ID {} mapped to URL {}", instanceId, serviceId,
                    url);
                instances.add(builder.build());
            }
        } catch (MalformedURLException e) {
            throw new ServiceDefinitionException(String.format("The URL %s is malformed. The instance of %s will not be created: %s",
                instanceBaseUrl, serviceId, e.getMessage()));
        } catch (UnknownHostException e) {
            throw new ServiceDefinitionException(String.format("The hostname of URL %s is unknown. The instance of %s will not be created: %s",
                instanceBaseUrl, serviceId, e.getMessage()));
        } catch (MetadataValidationException mve) {
            throw new ServiceDefinitionException(String.format("Metadata creation failed. The instance of %s will not be created: %s",
                serviceId, mve));
        }
    }

    private void setInstanceAttributes(InstanceInfo.Builder builder,
                                       Service service,
                                       String instanceId, String instanceBaseUrl,
                                       URL url,
                                       String ipAddress,
                                       CatalogUiTile tile) {
        String serviceId = service.getServiceId();

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

    private void setPort(InstanceInfo.Builder builder,
                         Service service,
                         String instanceBaseUrl,
                         URL url) throws MalformedURLException {
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

        mt.put(VERSION, CURRENT_VERSION);
        mt.put(SERVICE_TITLE, service.getTitle());
        mt.put(SERVICE_DESCRIPTION, service.getDescription());

        if (service.getRoutes() != null) {
            for (Route rs : service.getRoutes()) {
                String gatewayUrl = UrlUtils.trimSlashes(rs.getGatewayUrl());
                String key = gatewayUrl.replace("/", "-");
                String serviceUrl = url.getPath()
                    + (rs.getServiceRelativeUrl() == null ? "" : rs.getServiceRelativeUrl());
                mt.put(String.format("%s.%s.%s", ROUTES, key, ROUTES_GATEWAY_URL), gatewayUrl);
                mt.put(String.format("%s.%s.%s", ROUTES, key, ROUTES_SERVICE_URL), serviceUrl);
            }
        }

        if (tile != null) {
            mt.put(CATALOG_ID, tile.getId());
            mt.put(CATALOG_VERSION, DEFAULT_TILE_VERSION);
            mt.put(CATALOG_TITLE, tile.getTitle());
            mt.put(CATALOG_DESCRIPTION, tile.getDescription());
        }

        if (service.getApiInfo() != null) {
            for (ApiInfo apiInfo : service.getApiInfo()) {
                mt.putAll(EurekaMetadataParser.generateMetadata(service.getServiceId(), apiInfo));
            }
        }

        return mt;
    }
}
