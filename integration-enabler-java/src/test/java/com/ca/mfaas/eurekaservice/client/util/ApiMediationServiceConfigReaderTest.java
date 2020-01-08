/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package com.ca.mfaas.eurekaservice.client.util;

import com.ca.mfaas.eurekaservice.client.config.ApiMediationServiceConfig;
import com.ca.mfaas.exception.ServiceDefinitionException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.mock.web.MockServletContext;

import javax.servlet.ServletContext;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.*;

import static junit.framework.TestCase.assertNull;
import static org.hamcrest.core.Is.isA;
import static org.junit.Assert.*;

public class ApiMediationServiceConfigReaderTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void testLoadConfiguration_TwoFiles_OK() throws ServiceDefinitionException {

        String internalFileName = "/service-configuration.yml";
        String additionalFileName = "/additional-service-configuration.yml";

        ApiMediationServiceConfigReader apiMediationServiceConfigReader = new ApiMediationServiceConfigReader();
        ApiMediationServiceConfig result = apiMediationServiceConfigReader.loadConfiguration(internalFileName, additionalFileName);

        assertNotNull(result);
        assertEquals("hellopje", result.getServiceId());

        // Use default internal file name
        result = apiMediationServiceConfigReader.loadConfiguration(null, additionalFileName);

        assertNotNull(result);
        assertEquals("hellopje", result.getServiceId());
    }

    @Test
    public void readConfigurationWithWrongFormat() throws ServiceDefinitionException {
        String file = "/bad-format-of-service-configuration.yml";
        exceptionRule.expect(ServiceDefinitionException.class);

        ApiMediationServiceConfig  config = new ApiMediationServiceConfigReader().loadConfiguration(file);
        assertNull(config);
    }

    @Test
    public void testMapMerge_FULL() throws ServiceDefinitionException {
        ApiMediationServiceConfig apimlServcieConfig1 = getApiMediationServiceConfigFromFile( "/service-configuration.yml", null);
        ApiMediationServiceConfig apimlServcieConfig2 = getApiMediationServiceConfigFromFile( "/additional-service-configuration.yml", null);

        ApiMediationServiceConfig apiMediationServiceConfig = new ApiMediationServiceConfigReader().mergeConfigurations(apimlServcieConfig1, apimlServcieConfig2);

        assertNotNull(apiMediationServiceConfig);
        assertEquals("../keystore/localhost/localhost.truststore.p12", apiMediationServiceConfig.getSsl().getTrustStore());
        assertEquals("password2", apiMediationServiceConfig.getSsl().getTrustStorePassword());

        apiMediationServiceConfig = new ApiMediationServiceConfigReader().mergeConfigurations(apimlServcieConfig1, null);
        assertNotNull(apiMediationServiceConfig);
        assertEquals(apiMediationServiceConfig, apimlServcieConfig1);
        assertEquals("keystore/localhost/localhost.truststore.p12", apiMediationServiceConfig.getSsl().getTrustStore());
        assertEquals("password", apiMediationServiceConfig.getSsl().getTrustStorePassword());

        apiMediationServiceConfig = new ApiMediationServiceConfigReader().mergeConfigurations(null, apimlServcieConfig2);
        assertNotNull(apiMediationServiceConfig);
        assertEquals(apiMediationServiceConfig, apimlServcieConfig2);
        assertEquals("../keystore/localhost/localhost.truststore.p12", apiMediationServiceConfig.getSsl().getTrustStore());
        assertEquals("password2", apiMediationServiceConfig.getSsl().getTrustStorePassword());
    }

    @Test
    public void testMapMerge_PART_serviceid_keystore_truststore() throws ServiceDefinitionException {

        ApiMediationServiceConfig apimlServcieConfig1 = getApiMediationServiceConfigFromFile( "/service-configuration.yml", null);
        ApiMediationServiceConfig apimlServcieConfig2 = getApiMediationServiceConfigFromFile( "/additional-service-configuration_serviceid-andkeystore-truststore-only.yml", null);

        ApiMediationServiceConfig apiMediationServiceConfig = new ApiMediationServiceConfigReader().mergeConfigurations(apimlServcieConfig1, apimlServcieConfig2);

        assertNotNull(apiMediationServiceConfig);
        assertEquals("hellozowe", apiMediationServiceConfig.getServiceId());
        assertEquals("hello-zowe", apiMediationServiceConfig.getCatalog().getTile().getId());
        assertEquals("../keystore/localhost/localhost.keystore.p12", apiMediationServiceConfig.getSsl().getKeyStore());
        assertEquals("password1", apiMediationServiceConfig.getSsl().getKeyStorePassword());
        assertEquals("../truststore/localhost/localhost.truststore.p12", apiMediationServiceConfig.getSsl().getTrustStore());
        assertEquals("password2", apiMediationServiceConfig.getSsl().getTrustStorePassword());
    }

    @Test
    public void testMapMerge_PART_serviceid_ciphers() throws ServiceDefinitionException {
        ApiMediationServiceConfig apimlServcieConfig1 = getApiMediationServiceConfigFromFile( "/service-configuration.yml", null);
        ApiMediationServiceConfig apimlServcieConfig2 = getApiMediationServiceConfigFromFile( "/additional-service-configuration_serviceid-ssl-ciphers-only.yml", null);

        ApiMediationServiceConfig apiMediationServiceConfig = new ApiMediationServiceConfigReader().mergeConfigurations(apimlServcieConfig1, apimlServcieConfig2);
        assertNotNull(apiMediationServiceConfig);
        assertEquals("hellopje", apiMediationServiceConfig.getServiceId());
        assertEquals("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256,TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256,TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384", apiMediationServiceConfig.getSsl().getCiphers());
        assertEquals("keystore/localhost/localhost.keystore.p12", apiMediationServiceConfig.getSsl().getKeyStore());
        assertEquals("password", apiMediationServiceConfig.getSsl().getKeyStorePassword());
        assertEquals("keystore/localhost/localhost.truststore.p12", apiMediationServiceConfig.getSsl().getTrustStore());
        assertEquals("password", apiMediationServiceConfig.getSsl().getTrustStorePassword());
    }

    @Test
    public void testGetApiMediationServiceConfigFromFile() throws ServiceDefinitionException {
        Map<String, String> properties = new HashMap<>();
        ApiMediationServiceConfig config = getApiMediationServiceConfigFromFile("/service-configuration.yml", properties);
        assertNotNull(config);

        config = getApiMediationServiceConfigFromFile(null, properties);
        assertNull(config);
    }

    private ApiMediationServiceConfig getApiMediationServiceConfigFromFile(String fileName, Map<String, String> properties) throws ServiceDefinitionException {

        ApiMediationServiceConfigReader apiMediationServiceConfigReader = new ApiMediationServiceConfigReader();

        return apiMediationServiceConfigReader.buildConfiguration(fileName, properties);
    }

    private void checkContextMap(Map<String, String> contextMap) {
        assertNotNull(contextMap);
        assertNull(contextMap.get("NOT-AN-apiml.config.location"));
        assertEquals("/service-configuration.yml", contextMap.get("apiml.config.location"));
        assertEquals("../config/local/helloworld-additional-config.yml", contextMap.get("apiml.config.additional-location"));
        assertEquals("127.0.0.2", contextMap.get("apiml.serviceIpAddress"));
        assertEquals("10011", contextMap.get("apiml.discoveryService.port"));
        assertEquals("localhost", contextMap.get("apiml.discoveryService.hostname"));
        assertEquals("true", contextMap.get("apiml.ssl.enabled"));
        assertEquals("true", contextMap.get("apiml.ssl.verifySslCertificatesOfServices"));
        assertEquals("password", contextMap.get("apiml.ssl.keyPassword"));
        assertEquals("password", contextMap.get("apiml.ssl.keyStorePassword"));
        assertEquals("password", contextMap.get("apiml.ssl.trustStorePassword"));
    }


    @Test
    public void testSetApiMlContext_Ok() {
        ServletContext context = getMockServletContext();

        ApiMediationServiceConfigReader reader = new ApiMediationServiceConfigReader();
        Map<String, String>  contextMap = reader.setApiMlServiceContext(context);
        checkContextMap(contextMap);
    }

    @Test
    public void testSetApiMlContextTwice_Ok() {
        ServletContext context = getMockServletContext();

        ApiMediationServiceConfigReader reader = new ApiMediationServiceConfigReader();
        Map<String, String>  contextMap = reader.setApiMlServiceContext(context);

        checkContextMap(contextMap);
    }

    @Test
    public void testLoadConfigurationFromContext_OK() throws ServiceDefinitionException {
        ServletContext context = getMockServletContext();
        ApiMediationServiceConfigReader reader = new ApiMediationServiceConfigReader();
        ApiMediationServiceConfig config = reader.loadConfiguration(context);
        assertNotNull(config);
    }

    @Test
    public void testLoadConfigurationFromSingleFile_OK() throws ServiceDefinitionException {
        ApiMediationServiceConfigReader reader = new ApiMediationServiceConfigReader();
        ApiMediationServiceConfig config = reader.loadConfiguration("/service-configuration.yml");
        assertNotNull(config);
    }

    private ServletContext getMockServletContext() {
        ServletContext context = new MockServletContext();
        context.setInitParameter("NOT-AN-apiml.config.location", "/service-config.yml");
        context.setInitParameter("apiml.config.location", "/service-configuration.yml");
        context.setInitParameter("apiml.config.additional-location", "../config/local/helloworld-additional-config.yml");
        context.setInitParameter("apiml.serviceIpAddress", "127.0.0.2");
        context.setInitParameter("apiml.discoveryService.port", "10011");
        context.setInitParameter("apiml.discoveryService.hostname", "localhost");
        context.setInitParameter("apiml.ssl.enabled", "true");
        context.setInitParameter("apiml.ssl.verifySslCertificatesOfServices", "true");
        context.setInitParameter("apiml.ssl.keyPassword", "password");
        context.setInitParameter("apiml.ssl.keyStorePassword", "password");
        context.setInitParameter("apiml.ssl.trustStorePassword", "password");
        return context;
    }


    @Test
    public void testReadConfigurationFile_Internal_file_name_null() throws ServiceDefinitionException {

        ApiMediationServiceConfigReader apiMediationServiceConfigReader = new ApiMediationServiceConfigReader();

        // 1) Existing file
        String internalFileName = "/service-configuration.yml";
        ApiMediationServiceConfig result = apiMediationServiceConfigReader.loadConfiguration(null, internalFileName);
        assertNotNull(result);
    }

    @Test
    public void testLoadConfiguration_IpAddressIsNull_OK() throws ServiceDefinitionException {
        String internalFileName = "/additional-service-configuration_ip-address-null.yml";
        String additionalFileName = "/additional-service-configuration_ip-address-null.yml";

        ApiMediationServiceConfigReader apiMediationServiceConfigReader = new ApiMediationServiceConfigReader();
        ApiMediationServiceConfig result = apiMediationServiceConfigReader.loadConfiguration(internalFileName, additionalFileName);

        assertNotNull(result);
        assertEquals("service", result.getServiceId());
    }

    @Test
    public void testLoadConfiguration_IpAddressIsNull_bad_baseUrl() throws ServiceDefinitionException {
        exceptionRule.expect(ServiceDefinitionException.class);
        exceptionRule.expectCause(isA(MalformedURLException.class));

        String internalFileName = "/additional-service-configuration_ip-address-null_bad-baseUrl.yml";
        String additionalFileName = "/additional-service-configuration_ip-address-null_bad-baseUrl.yml";

        ApiMediationServiceConfigReader apiMediationServiceConfigReader = new ApiMediationServiceConfigReader();
        // ApiMediationServiceConfig result =
            apiMediationServiceConfigReader.loadConfiguration(internalFileName, additionalFileName);
    }

    @Test
    public void testLoadConfiguration_IpAddressIsNull_UnknownHost() throws ServiceDefinitionException {
        exceptionRule.expect(ServiceDefinitionException.class);
        exceptionRule.expectCause(isA(UnknownHostException.class));

        String internalFileName = "/additional-service-configuration_ip-address-null_UnknownHost.yml";

        ApiMediationServiceConfigReader apiMediationServiceConfigReader = new ApiMediationServiceConfigReader();
        // ApiMediationServiceConfig result =
            apiMediationServiceConfigReader.loadConfiguration(internalFileName, null);

    }
}
