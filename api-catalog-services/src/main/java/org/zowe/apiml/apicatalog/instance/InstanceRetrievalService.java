/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.apicatalog.instance;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.converters.jackson.EurekaJsonJacksonCodec;
import com.netflix.discovery.shared.Applications;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.zowe.apiml.apicatalog.discovery.DiscoveryConfigProperties;
import org.zowe.apiml.message.log.ApimlLogger;
import org.zowe.apiml.product.instance.InstanceInitializationException;
import org.zowe.apiml.product.logging.annotations.InjectApimlLogger;
import org.zowe.apiml.product.registry.ApplicationWrapper;

import javax.validation.constraints.NotBlank;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * Service for instance retrieval from Eureka
 */
@Slf4j
@Service
public class InstanceRetrievalService {

    private final DiscoveryConfigProperties discoveryConfigProperties;
    private final RestTemplate restTemplate;

    private static final String APPS_ENDPOINT = "apps/";
    private static final String DELTA_ENDPOINT = "delta";
    private static final String UNKNOWN = "unknown";

    @InjectApimlLogger
    private final ApimlLogger apimlLog = ApimlLogger.empty();

    @Autowired
    public InstanceRetrievalService(DiscoveryConfigProperties discoveryConfigProperties,
                                    RestTemplate restTemplate) {
        this.discoveryConfigProperties = discoveryConfigProperties;
        this.restTemplate = restTemplate;

        configureUnicode(restTemplate);
    }

    /**
     * Retrieves {@link InstanceInfo} of particular service
     *
     * @param serviceId the service to search for
     * @return service instance
     */
    public InstanceInfo getInstanceInfo(@NotBlank(message = "Service Id must be supplied") String serviceId) {
        if (serviceId.equalsIgnoreCase(UNKNOWN)) {
            return null;
        }

            List<Pair<String, Pair<String, String>>> requestInfoList = constructServiceInfoQueryRequest(serviceId, false);
            // iterate over list of discovery services, return at first success
            for (Pair<String, Pair<String, String>> requestInfo : requestInfoList) {
                // call Eureka REST endpoint to fetch single or all Instances
                    try {
                        ResponseEntity<String> response = queryDiscoveryForInstances(requestInfo);
                        if (response.getStatusCode().is2xxSuccessful()) {
                            return extractSingleInstanceFromApplication(serviceId, requestInfo.getLeft(), response);
                        }
                    } catch (Exception e) {
                        log.debug("Error getting instance info from {}, error message: {}", requestInfo.getLeft(), e.getMessage());
                    }
            }
        String msg = "An error occurred when trying to get instance info for:  " + serviceId;
        throw new InstanceInitializationException(msg);
    }

    /**
     * Retrieve instances from the discovery service
     *
     * @param delta filter the registry information to the just updated infos
     * @return the Applications object that wraps all the registry information
     */
    public Applications getAllInstancesFromDiscovery(boolean delta) {

        List<Pair<String, Pair<String, String>>> requestInfoList = constructServiceInfoQueryRequest(null, delta);
        for (Pair<String, Pair<String, String>> requestInfo : requestInfoList) {
            try {
                ResponseEntity<String> response = queryDiscoveryForInstances(requestInfo);
                return extractApplications(requestInfo, response);
            } catch (Exception e) {
                log.debug("Not able to contact discovery service: " + requestInfo.getKey(), e);
            }
        }
        //  call Eureka REST endpoint to fetch single or all Instances
        return null;
    }

    /**
     * Parse information from the response and extract the Applications object which contains all the registry information returned by eureka server
     *
     * @param requestInfo contains the pair of discovery URL and discovery credentials (for HTTP access)
     * @param response    the http response
     * @return Applications object that wraps all the registry information
     */
    private Applications extractApplications(Pair<String, Pair<String, String>> requestInfo, ResponseEntity<String> response) {
        Applications applications = null;
        if (!HttpStatus.OK.equals(response.getStatusCode()) || response.getBody() == null) {
            apimlLog.log("org.zowe.apiml.apicatalog.serviceRetrievalRequestFailed", response.getStatusCode(), response.getStatusCode().getReasonPhrase(), requestInfo.getLeft());
        } else {
            ObjectMapper mapper = new EurekaJsonJacksonCodec().getObjectMapper(Applications.class);
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            try {
                applications = mapper.readValue(response.getBody(), Applications.class);
            } catch (IOException e) {
                apimlLog.log("org.zowe.apiml.apicatalog.serviceRetrievalParsingFailed", e.getMessage());
            }
        }
        return applications;
    }

    /**
     * Query Discovery
     *
     * @param requestInfo information used to query the discovery service
     * @return ResponseEntity<String> query response
     */
    private ResponseEntity<String> queryDiscoveryForInstances(Pair<String, Pair<String, String>> requestInfo) {
        HttpEntity<?> entity = new HttpEntity<>(null, createRequestHeader(requestInfo.getRight()));
        ResponseEntity<String> response = restTemplate.exchange(
            requestInfo.getLeft(),
            HttpMethod.GET,
            entity,
            String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            log.debug("Could not locate instance for request: " + requestInfo.getLeft()
                + ", " + response.getStatusCode() + " = " + response.getStatusCode().getReasonPhrase());
        }
        return response;
    }

    /**
     * @param serviceId the service to search for
     * @param url       try to find instance with this discovery url
     * @param response  the fetch attempt response
     * @return service instance
     */
    private InstanceInfo extractSingleInstanceFromApplication(String serviceId, String url, ResponseEntity<String> response) {
        ApplicationWrapper application = null;
        if (!HttpStatus.OK.equals(response.getStatusCode()) || response.getBody() == null) {
            log.debug("Could not retrieve service: " + serviceId + " instance info from discovery --" + response.getStatusCode()
                + " -- " + response.getStatusCode().getReasonPhrase() + " -- URL: " + url);
            return null;
        } else {
            ObjectMapper mapper = new ObjectMapper();
            mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            try {
                application = mapper.readValue(response.getBody(), ApplicationWrapper.class);
            } catch (IOException e) {
                log.debug("Could not extract service: " + serviceId + " info from discovery --" + e.getMessage(), e);
            }
        }

        if (application != null
            && application.getApplication() != null
            && application.getApplication().getInstances() != null
            && !application.getApplication().getInstances().isEmpty()) {
            return application.getApplication().getInstances().get(0);
        } else {
            return null;
        }
    }

    /**
     * Construct a tuple used to query the discovery service
     *
     * @param serviceId optional service id
     * @return request information
     */
    private List<Pair<String, Pair<String, String>>> constructServiceInfoQueryRequest(String serviceId, boolean getDelta) {
        String[] discoveryServiceUrls = discoveryConfigProperties.getLocations().split(",");
        List<Pair<String, Pair<String, String>>> discoveryPairs = new ArrayList<>(discoveryServiceUrls.length);
        for (String discoveryUrl : discoveryServiceUrls) {
            String discoveryServiceLocatorUrl = discoveryUrl + APPS_ENDPOINT;
            if (getDelta) {
                discoveryServiceLocatorUrl += DELTA_ENDPOINT;
            } else {
                if (serviceId != null) {
                    discoveryServiceLocatorUrl += serviceId.toLowerCase();
                }
            }

            String eurekaUsername = discoveryConfigProperties.getEurekaUserName();
            String eurekaUserPassword = discoveryConfigProperties.getEurekaUserPassword();

            Pair<String, String> discoveryServiceCredentials = Pair.of(eurekaUsername, eurekaUserPassword);

            log.debug("Eureka credentials retrieved for user: {} {}",
                eurekaUsername,
                (!eurekaUserPassword.isEmpty() ? "*******" : "NO PASSWORD")
            );

            log.debug("Checking instance info from: " + discoveryServiceLocatorUrl);
            discoveryPairs.add(Pair.of(discoveryServiceLocatorUrl, discoveryServiceCredentials));
        }
        return discoveryPairs;
    }

    /**
     * Create HTTP headers
     *
     * @return HTTP Headers
     */
    private HttpHeaders createRequestHeader(Pair<String, String> credentials) {
        HttpHeaders headers = new HttpHeaders();
        if (credentials != null && credentials.getLeft() != null && credentials.getRight() != null) {
            String basicToken = "Basic " + Base64.getEncoder().encodeToString((credentials.getLeft() + ":"
                + credentials.getRight()).getBytes());
            headers.add("Authorization", basicToken);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(new ArrayList<>(Collections.singletonList(MediaType.APPLICATION_JSON)));
        return headers;
    }

    private void configureUnicode(RestTemplate restTemplate) {
        restTemplate.getMessageConverters()
            .add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
    }
}
