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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.zowe.apiml.apicatalog.instance.InstanceRetrievalService;
import org.zowe.apiml.apicatalog.services.cached.model.ApiDocInfo;
import org.zowe.apiml.apicatalog.services.status.model.ApiDocNotFoundException;
import org.zowe.apiml.apicatalog.services.status.model.ApiVersionNotFoundException;
import org.zowe.apiml.product.gateway.GatewayClient;
import org.zowe.apiml.product.gateway.GatewayConfigProperties;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.zowe.apiml.constants.EurekaMetadataDefinition.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LocalApiDocServiceTest {
    private static final String SERVICE_ID = "service";
    private static final String ZOSMF_ID = "ibmzosmf";
    private static final String SERVICE_HOST = "service";
    private static final int SERVICE_PORT = 8080;
    private static final String SERVICE_VERSION = "1.0.0";
    private static final String HIGHER_SERVICE_VERSION = "2.0.0";
    private static final String SERVICE_VERSION_V = "v1";
    private static final String HIGHER_SERVICE_VERSION_V = "v2";
    private static final String GATEWAY_SCHEME = "http";
    private static final String GATEWAY_HOST = "gateway:10000";
    private static final String GATEWAY_URL = "api/v1";
    private static final String API_ID = "test.app";
    private static final String SWAGGER_URL = "https://service:8080/service/api-doc";

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private InstanceRetrievalService instanceRetrievalService;

    private APIDocRetrievalService apiDocRetrievalService;

    @BeforeEach
    void setup() {
        GatewayClient gatewayClient = new GatewayClient(getProperties());
        apiDocRetrievalService = new APIDocRetrievalService(
            restTemplate,
            instanceRetrievalService,
            gatewayClient);
    }

    @Test
    void testRetrievalOfAPIDoc() {
        String responseBody = "api-doc body";

        when(instanceRetrievalService.getInstanceInfo(SERVICE_ID))
            .thenReturn(getStandardInstance(getStandardMetadata(), true));

        ResponseEntity<String> expectedResponse = new ResponseEntity<>(responseBody, HttpStatus.OK);
        when(restTemplate.exchange(SWAGGER_URL, HttpMethod.GET, getObjectHttpEntity(), String.class))
            .thenReturn(expectedResponse);

        ApiDocInfo actualResponse = apiDocRetrievalService.retrieveApiDoc(SERVICE_ID, SERVICE_VERSION);

        assertEquals(API_ID, actualResponse.getApiInfo().getApiId());
        assertEquals(GATEWAY_URL, actualResponse.getApiInfo().getGatewayUrl());
        assertEquals(SERVICE_VERSION, actualResponse.getApiInfo().getVersion());
        assertEquals(SWAGGER_URL, actualResponse.getApiInfo().getSwaggerUrl());

        assertNotNull(actualResponse);
        assertNotNull(actualResponse.getApiDocContent());
        assertEquals(responseBody, actualResponse.getApiDocContent());

        assertEquals("[api -> api=RoutedService(subServiceId=api-v1, gatewayUrl=api, serviceUrl=/)]", actualResponse.getRoutes().toString());
    }

    @Test
    void givenZosmfId_whenDocIsRequested_ValidDocIsProduced() {
        String responseBody = "api-doc [ null, null ] body";
        String expectedResponseBody = "api-doc [ true, false ] body";

        when(instanceRetrievalService.getInstanceInfo(ZOSMF_ID))
            .thenReturn(getStandardInstance(getStandardMetadata(), true));

        ResponseEntity<String> expectedResponse = new ResponseEntity<>(responseBody, HttpStatus.OK);
        when(restTemplate.exchange(SWAGGER_URL, HttpMethod.GET, getObjectHttpEntity(), String.class))
            .thenReturn(expectedResponse);

        ApiDocInfo actualResponse = apiDocRetrievalService.retrieveApiDoc(ZOSMF_ID, SERVICE_VERSION);

        assertEquals(API_ID, actualResponse.getApiInfo().getApiId());
        assertEquals(GATEWAY_URL, actualResponse.getApiInfo().getGatewayUrl());
        assertEquals(SERVICE_VERSION, actualResponse.getApiInfo().getVersion());
        assertEquals(SWAGGER_URL, actualResponse.getApiInfo().getSwaggerUrl());

        assertNotNull(actualResponse);
        assertNotNull(actualResponse.getApiDocContent());
        assertEquals(expectedResponseBody, actualResponse.getApiDocContent());

        assertEquals("[api -> api=RoutedService(subServiceId=api-v1, gatewayUrl=api, serviceUrl=/)]", actualResponse.getRoutes().toString());
    }

    @Test
    void givenZosmfId_whenIncorrectResponseFromServer_thenReturnDefaultDoc() {
        String responseBody = "Server not found";

        when(instanceRetrievalService.getInstanceInfo(ZOSMF_ID))
            .thenReturn(getStandardInstance(getStandardMetadata(), true));

        ResponseEntity<String> expectedResponse = new ResponseEntity<>(responseBody, HttpStatus.INTERNAL_SERVER_ERROR);
        when(restTemplate.exchange(SWAGGER_URL, HttpMethod.GET, getObjectHttpEntity(), String.class))
            .thenReturn(expectedResponse);

        ApiDocInfo result = apiDocRetrievalService.retrieveApiDoc(ZOSMF_ID, SERVICE_VERSION);
        assertThat(result.getApiDocContent(), is(notNullValue()));
    }

    @Test
    void testFailedRetrievalOfAPIDocWhenServiceNotFound() {
        Exception exception = assertThrows(ApiDocNotFoundException.class, () -> {
            apiDocRetrievalService.retrieveApiDoc(SERVICE_ID, SERVICE_VERSION);
        });
        assertEquals("Could not load instance information for service " + SERVICE_ID + ".", exception.getMessage());
    }

    @Test
    void testFailedRetrievalOfAPIDocWhenServerError() {
        String responseBody = "Server not found";

        when(instanceRetrievalService.getInstanceInfo(SERVICE_ID))
            .thenReturn(getStandardInstance(getStandardMetadata(), true));

        ResponseEntity<String> expectedResponse = new ResponseEntity<>(responseBody, HttpStatus.INTERNAL_SERVER_ERROR);
        when(restTemplate.exchange(SWAGGER_URL, HttpMethod.GET, getObjectHttpEntity(), String.class))
            .thenReturn(expectedResponse);

        Exception exception = assertThrows(ApiDocNotFoundException.class, () -> {
            apiDocRetrievalService.retrieveApiDoc(SERVICE_ID, SERVICE_VERSION);
        });
        assertEquals("No API Documentation was retrieved due to " + SERVICE_ID + " server error: '" + responseBody + "'.", exception.getMessage());
    }

    @Test
    void testFailedRetrievalOfAPIDocWhenMetadataNotDefined() {
        String responseBody = "api-doc body";

        when(instanceRetrievalService.getInstanceInfo(SERVICE_ID))
            .thenReturn(getStandardInstance(new HashMap<>(), true));

        ResponseEntity<String> expectedResponse = new ResponseEntity<>(responseBody, HttpStatus.OK);
        when(restTemplate.exchange(SWAGGER_URL, HttpMethod.GET, getObjectHttpEntity(), String.class))
            .thenReturn(expectedResponse);

        Exception exception = assertThrows(ApiDocNotFoundException.class, () -> {
            apiDocRetrievalService.retrieveApiDoc(SERVICE_ID, SERVICE_VERSION);
        });
        assertEquals("No API Documentation defined for service " + SERVICE_ID + ".", exception.getMessage());
    }

    @Test
    void shouldGenerateSubstituteSwaggerIfSwaggerUrlNull() {
        String generatedResponseBody = "{\n" +
            "    \"swagger\": \"2.0\",\n" +
            "    \"info\": {\n" +
            "        \"title\": \"Test service\"\n" +
            "      , \"description\": \"Test service description\"\n" +
            "      , \"version\": \"1.0.0\"\n" +
            "    },\n" +
            "    \"host\": \"gateway:10000\",\n" +
            "    \"basePath\": \"/service/api/v1\",\n" +
            "    \"schemes\": [\"http\"],\n" +
            "    \"tags\": [\n" +
            "        {\n" +
            "            \"name\": \"apimlHidden\"\n" +
            "        }\n" +
            "    ],\n" +
            "    \"paths\": {\n" +
            "        \"/apimlHidden\": {\n" +
            "            \"get\": {\n" +
            "                \"tags\": [\"apimlHidden\"],\n" +
            "                \"responses\": {\n" +
            "                    \"200\": {\n" +
            "                        \"description\": \"OK\"\n" +
            "                    }\n" +
            "                }\n" +
            "            }\n" +
            "        }\n" +
            "    }\n" +
            "}\n";
        String responseBody = "api-doc body";

        generatedResponseBody = generatedResponseBody.replaceAll("\\s+", "");
        when(instanceRetrievalService.getInstanceInfo(SERVICE_ID))
            .thenReturn(getStandardInstance(getMetadataWithoutSwaggerUrl(), true));

        ResponseEntity<String> expectedResponse = new ResponseEntity<>(responseBody, HttpStatus.INTERNAL_SERVER_ERROR);
        when(restTemplate.exchange(SWAGGER_URL, HttpMethod.GET, getObjectHttpEntity(), String.class))
            .thenReturn(expectedResponse);

        ApiDocInfo actualResponse = apiDocRetrievalService.retrieveApiDoc(SERVICE_ID, SERVICE_VERSION);

        assertEquals(API_ID, actualResponse.getApiInfo().getApiId());
        assertEquals(GATEWAY_URL, actualResponse.getApiInfo().getGatewayUrl());
        assertEquals(SERVICE_VERSION, actualResponse.getApiInfo().getVersion());
        assertNull(actualResponse.getApiInfo().getSwaggerUrl());

        assertNotNull(actualResponse);
        assertNotNull(actualResponse.getApiDocContent());
        assertEquals(generatedResponseBody, actualResponse.getApiDocContent().replaceAll("\\s+", ""));

        assertEquals("[api -> api=RoutedService(subServiceId=api-v1, gatewayUrl=api, serviceUrl=/)]", actualResponse.getRoutes().toString());
    }

    @Test
    void shouldCreateApiDocUrlFromRouting() {
        String responseBody = "api-doc body";

        when(instanceRetrievalService.getInstanceInfo(SERVICE_ID))
            .thenReturn(getStandardInstance(getMetadataWithoutApiInfo(), true));

        ResponseEntity<String> expectedResponse = new ResponseEntity<>(responseBody, HttpStatus.OK);
        when(restTemplate.exchange(SWAGGER_URL, HttpMethod.GET, getObjectHttpEntity(), String.class))
            .thenReturn(expectedResponse);

        ApiDocInfo actualResponse = apiDocRetrievalService.retrieveApiDoc(SERVICE_ID, SERVICE_VERSION);

        assertNotNull(actualResponse);
        assertNotNull(actualResponse.getApiDocContent());

        assertEquals(responseBody, actualResponse.getApiDocContent());
    }

    @Test
    void shouldCreateApiDocUrlFromRoutingAndUseHttp() {
        String responseBody = "api-doc body";

        when(instanceRetrievalService.getInstanceInfo(SERVICE_ID))
            .thenReturn(getStandardInstance(getMetadataWithoutApiInfo(), false));

        ResponseEntity<String> expectedResponse = new ResponseEntity<>(responseBody, HttpStatus.OK);
        when(restTemplate.exchange("http://service:8080/service/api-doc", HttpMethod.GET, getObjectHttpEntity(), String.class))
            .thenReturn(expectedResponse);

        ApiDocInfo actualResponse = apiDocRetrievalService.retrieveApiDoc(SERVICE_ID, SERVICE_VERSION);

        assertNotNull(actualResponse);
        assertNotNull(actualResponse.getApiDocContent());

        assertEquals(responseBody, actualResponse.getApiDocContent());
    }

    @Test
    void givenDefaultApiDoc_whenRetrieveDefault_thenReturnIt() {
        String responseBody = "api-doc body";
        Map<String, String> metadata = getMetadataWithMultipleApiInfo();

        when(instanceRetrievalService.getInstanceInfo(SERVICE_ID))
            .thenReturn(getStandardInstance(metadata, true));

        ResponseEntity<String> expectedResponse = new ResponseEntity<>(responseBody, HttpStatus.OK);
        when(restTemplate.exchange(SWAGGER_URL, HttpMethod.GET, getObjectHttpEntity(), String.class))
            .thenReturn(expectedResponse);

        ApiDocInfo actualResponse = apiDocRetrievalService.retrieveDefaultApiDoc(SERVICE_ID);

        assertEquals(API_ID, actualResponse.getApiInfo().getApiId());
        assertEquals(GATEWAY_URL, actualResponse.getApiInfo().getGatewayUrl());
        assertEquals(SERVICE_VERSION, actualResponse.getApiInfo().getVersion());
        assertEquals(SWAGGER_URL, actualResponse.getApiInfo().getSwaggerUrl());

        assertNotNull(actualResponse);
        assertNotNull(actualResponse.getApiDocContent());
        assertEquals(responseBody, actualResponse.getApiDocContent());

        assertEquals("[api -> api=RoutedService(subServiceId=api-v1, gatewayUrl=api, serviceUrl=/)]", actualResponse.getRoutes().toString());
    }

    @Test
    void givenNoDefaultApiDoc_whenRetrieveDefault_thenReturnHighestVersion() {
        String responseBody = "api-doc body";
        Map<String, String> metadata = getMetadataWithMultipleApiInfo();
        metadata.remove(API_INFO + ".1." + API_INFO_IS_DEFAULT); // unset default API, so higher version becomes default

        when(instanceRetrievalService.getInstanceInfo(SERVICE_ID))
            .thenReturn(getStandardInstance(metadata, true));

        ResponseEntity<String> expectedResponse = new ResponseEntity<>(responseBody, HttpStatus.OK);
        when(restTemplate.exchange(SWAGGER_URL, HttpMethod.GET, getObjectHttpEntity(), String.class))
            .thenReturn(expectedResponse);

        ApiDocInfo actualResponse = apiDocRetrievalService.retrieveDefaultApiDoc(SERVICE_ID);

        assertEquals(API_ID, actualResponse.getApiInfo().getApiId());
        assertEquals(GATEWAY_URL, actualResponse.getApiInfo().getGatewayUrl());
        assertEquals(HIGHER_SERVICE_VERSION, actualResponse.getApiInfo().getVersion());
        assertEquals(SWAGGER_URL, actualResponse.getApiInfo().getSwaggerUrl());

        assertNotNull(actualResponse);
        assertNotNull(actualResponse.getApiDocContent());
        assertEquals(responseBody, actualResponse.getApiDocContent());

        assertEquals("[api -> api=RoutedService(subServiceId=api-v1, gatewayUrl=api, serviceUrl=/)]", actualResponse.getRoutes().toString());
    }

    @Test
    void givenNoDefaultApiDocAndDifferentVersionFormat_whenRetrieveDefault_thenReturnHighestVersion() {
        String responseBody = "api-doc body";

        when(instanceRetrievalService.getInstanceInfo(SERVICE_ID))
            .thenReturn(getStandardInstance(getMetadataWithMultipleApiInfoWithDifferentVersionFormat(), true));

        ResponseEntity<String> expectedResponse = new ResponseEntity<>(responseBody, HttpStatus.OK);
        when(restTemplate.exchange(SWAGGER_URL, HttpMethod.GET, getObjectHttpEntity(), String.class))
            .thenReturn(expectedResponse);

        ApiDocInfo actualResponse = apiDocRetrievalService.retrieveDefaultApiDoc(SERVICE_ID);

        assertEquals(API_ID, actualResponse.getApiInfo().getApiId());
        assertEquals(GATEWAY_URL, actualResponse.getApiInfo().getGatewayUrl());
        assertEquals(HIGHER_SERVICE_VERSION_V, actualResponse.getApiInfo().getVersion());
        assertEquals(SWAGGER_URL, actualResponse.getApiInfo().getSwaggerUrl());

        assertNotNull(actualResponse);
        assertNotNull(actualResponse.getApiDocContent());
        assertEquals(responseBody, actualResponse.getApiDocContent());

        assertEquals("[api -> api=RoutedService(subServiceId=api-v1, gatewayUrl=api, serviceUrl=/)]", actualResponse.getRoutes().toString());
    }

    @Test
    void givenNoApiDocs_whenRetrieveDefault_thenReturnNull() {
        String responseBody = "api-doc body";

        when(instanceRetrievalService.getInstanceInfo(SERVICE_ID))
            .thenReturn(getStandardInstance(getMetadataWithoutApiInfo(), true));

        ResponseEntity<String> expectedResponse = new ResponseEntity<>(responseBody, HttpStatus.OK);
        when(restTemplate.exchange(SWAGGER_URL, HttpMethod.GET, getObjectHttpEntity(), String.class))
            .thenReturn(expectedResponse);

        ApiDocInfo actualResponse = apiDocRetrievalService.retrieveDefaultApiDoc(SERVICE_ID);

        assertNotNull(actualResponse);
        assertNotNull(actualResponse.getApiDocContent());

        assertEquals(responseBody, actualResponse.getApiDocContent());
    }

    @Test
    void givenApiVersions_whenRetrieveVersions_thenReturnThem() {
        when(instanceRetrievalService.getInstanceInfo(SERVICE_ID))
            .thenReturn(getStandardInstance(getStandardMetadata(), false));

        List<String> actualVersions = apiDocRetrievalService.retrieveApiVersions(SERVICE_ID);
        assertEquals(Collections.singletonList("v1"), actualVersions);
    }

    @Test
    void givenNoApiVersions_whenRetrieveVersions_thenThrowException() {
        when(instanceRetrievalService.getInstanceInfo(SERVICE_ID)).thenReturn(null);

        Exception exception = assertThrows(ApiVersionNotFoundException.class, () -> {
            apiDocRetrievalService.retrieveApiVersions(SERVICE_ID);
        });
        assertEquals("Could not load instance information for service " + SERVICE_ID + ".", exception.getMessage());
    }

    @Test
    void givenDefaultApiVersion_whenRetrieveDefaultVersion_thenReturnIt() {
        when(instanceRetrievalService.getInstanceInfo(SERVICE_ID))
            .thenReturn(getStandardInstance(getMetadataWithMultipleApiInfo(), false));

        String defaultVersion = apiDocRetrievalService.retrieveDefaultApiVersion(SERVICE_ID);
        assertEquals("v1", defaultVersion);
    }

    @Test
    void givenNoDefaultApiVersion_whenRetrieveDefaultVersion_thenReturnHighestVersion() {
        Map<String, String> metadata = getMetadataWithMultipleApiInfo();
        metadata.remove(API_INFO + ".1." + API_INFO_IS_DEFAULT); // unset default API, so higher version becomes default

        when(instanceRetrievalService.getInstanceInfo(SERVICE_ID))
            .thenReturn(getStandardInstance(metadata, false));

        String defaultVersion = apiDocRetrievalService.retrieveDefaultApiVersion(SERVICE_ID);
        assertEquals("v2", defaultVersion);
    }

    @Test
    void givenNoApiInfo_whenRetrieveDefaultVersion_thenThrowException() {
        when(instanceRetrievalService.getInstanceInfo(SERVICE_ID)).thenReturn(null);

        Exception exception = assertThrows(ApiVersionNotFoundException.class, () -> {
            apiDocRetrievalService.retrieveDefaultApiVersion(SERVICE_ID);
        });
        assertEquals("Could not load instance information for service " + SERVICE_ID + ".", exception.getMessage());
    }

    private HttpEntity<Object> getObjectHttpEntity() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON_UTF8));

        return new HttpEntity<>(headers);
    }

    private InstanceInfo getStandardInstance(Map<String, String> metadata, Boolean isPortSecure) {
        return InstanceInfo.Builder.newBuilder()
            .setAppName(SERVICE_ID)
            .setHostName(SERVICE_HOST)
            .setPort(SERVICE_PORT)
            .setSecurePort(SERVICE_PORT)
            .enablePort(InstanceInfo.PortType.SECURE, isPortSecure)
            .setStatus(InstanceInfo.InstanceStatus.UP)
            .setMetadata(metadata)
            .build();
    }

    private Map<String, String> getStandardMetadata() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put(API_INFO + ".1." + API_INFO_API_ID, API_ID);
        metadata.put(API_INFO + ".1." + API_INFO_GATEWAY_URL, GATEWAY_URL);
        metadata.put(API_INFO + ".1." + API_INFO_VERSION, SERVICE_VERSION);
        metadata.put(API_INFO + ".1." + API_INFO_SWAGGER_URL, SWAGGER_URL);
        metadata.put(ROUTES + ".api-v1." + ROUTES_GATEWAY_URL, "api");
        metadata.put(ROUTES + ".api-v1." + ROUTES_SERVICE_URL, "/");
        metadata.put(SERVICE_TITLE, "Test service");
        metadata.put(SERVICE_DESCRIPTION, "Test service description");

        return metadata;
    }

    private Map<String, String> getMetadataWithoutSwaggerUrl() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put(API_INFO + ".1." + API_INFO_API_ID, API_ID);
        metadata.put(API_INFO + ".1." + API_INFO_GATEWAY_URL, GATEWAY_URL);
        metadata.put(API_INFO + ".1." + API_INFO_VERSION, SERVICE_VERSION);
        metadata.put(ROUTES + ".api-v1." + ROUTES_GATEWAY_URL, "api");
        metadata.put(ROUTES + ".api-v1." + ROUTES_SERVICE_URL, "/");
        metadata.put(SERVICE_TITLE, "Test service");
        metadata.put(SERVICE_DESCRIPTION, "Test service description");

        return metadata;
    }

    private Map<String, String> getMetadataWithMultipleApiInfo() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put(API_INFO + ".1." + API_INFO_API_ID, API_ID);
        metadata.put(API_INFO + ".1." + API_INFO_GATEWAY_URL, GATEWAY_URL);
        metadata.put(API_INFO + ".1." + API_INFO_VERSION, SERVICE_VERSION);
        metadata.put(API_INFO + ".1." + API_INFO_SWAGGER_URL, SWAGGER_URL);
        metadata.put(API_INFO + ".1." + API_INFO_IS_DEFAULT, "true");

        metadata.put(API_INFO + ".2." + API_INFO_API_ID, API_ID);
        metadata.put(API_INFO + ".2." + API_INFO_GATEWAY_URL, GATEWAY_URL);
        metadata.put(API_INFO + ".2." + API_INFO_VERSION, HIGHER_SERVICE_VERSION);
        metadata.put(API_INFO + ".2." + API_INFO_SWAGGER_URL, SWAGGER_URL);

        metadata.put(ROUTES + ".api-v1." + ROUTES_GATEWAY_URL, "api");
        metadata.put(ROUTES + ".api-v1." + ROUTES_SERVICE_URL, "/");
        metadata.put(SERVICE_TITLE, "Test service");
        metadata.put(SERVICE_DESCRIPTION, "Test service description");

        return metadata;
    }

    private Map<String, String> getMetadataWithMultipleApiInfoWithDifferentVersionFormat() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put(API_INFO + ".1." + API_INFO_API_ID, API_ID);
        metadata.put(API_INFO + ".1." + API_INFO_GATEWAY_URL, GATEWAY_URL);
        metadata.put(API_INFO + ".1." + API_INFO_VERSION, SERVICE_VERSION_V);
        metadata.put(API_INFO + ".1." + API_INFO_SWAGGER_URL, SWAGGER_URL);

        metadata.put(API_INFO + ".2." + API_INFO_API_ID, API_ID);
        metadata.put(API_INFO + ".2." + API_INFO_GATEWAY_URL, GATEWAY_URL);
        metadata.put(API_INFO + ".2." + API_INFO_VERSION, HIGHER_SERVICE_VERSION_V);
        metadata.put(API_INFO + ".2." + API_INFO_SWAGGER_URL, SWAGGER_URL);

        metadata.put(ROUTES + ".api-v1." + ROUTES_GATEWAY_URL, "api");
        metadata.put(ROUTES + ".api-v1." + ROUTES_SERVICE_URL, "/");
        metadata.put(SERVICE_TITLE, "Test service");
        metadata.put(SERVICE_DESCRIPTION, "Test service description");

        return metadata;
    }

    private Map<String, String> getMetadataWithoutApiInfo() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put(ROUTES + ".api-v1." + ROUTES_GATEWAY_URL, "api");
        metadata.put(ROUTES + ".api-v1." + ROUTES_SERVICE_URL, "/");
        metadata.put(ROUTES + ".apidoc." + ROUTES_GATEWAY_URL, "api/v1/api-doc");
        metadata.put(ROUTES + ".apidoc." + ROUTES_SERVICE_URL, SERVICE_ID + "/api-doc");
        metadata.put(SERVICE_TITLE, "Test service");
        metadata.put(SERVICE_DESCRIPTION, "Test service description");

        return metadata;
    }

    private GatewayConfigProperties getProperties() {
        return GatewayConfigProperties.builder()
            .scheme(GATEWAY_SCHEME)
            .hostname(GATEWAY_HOST)
            .build();
    }
}
