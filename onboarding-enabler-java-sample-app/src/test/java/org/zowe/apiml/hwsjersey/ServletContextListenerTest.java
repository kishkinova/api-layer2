/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.hwsjersey;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockServletContext;
import org.zowe.apiml.eurekaservice.client.ApiMediationClient;
import org.zowe.apiml.exception.ServiceDefinitionException;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;


class ServletContextListenerTest {
    @Test
    void testOnContextCreationRegisterWithEureka() throws ServiceDefinitionException {
        ApiMediationClient mock = Mockito.mock(ApiMediationClient.class);
        ServletContext context = setValidParametersTo(
            new MockServletContext()
        );

        HelloJerseyListener registrator = new HelloJerseyListener(mock);
        registrator.contextInitialized(new ServletContextEvent(context));

        // Verify that the mock register is called.
        verify(mock).register(any());
    }

    @Test
    void testOnContextDestroyUnregisterWithEureka() {
        ApiMediationClient mock = Mockito.mock(ApiMediationClient.class);
        setValidParametersTo(
            new MockServletContext()
        );

        HelloJerseyListener registrator = new HelloJerseyListener(mock);
        registrator.contextDestroyed(null);

        // Verify that the mock unregister is called.
        verify(mock).unregister();
    }

    private ServletContext setValidParametersTo(ServletContext context) {
        context.setInitParameter("apiml.config.location", "/service-config.yml");
        context.setInitParameter("apiml.config.additional-location", "../config/local/helloworld-additional-config.yml");
        context.setInitParameter("apiml.serviceIpAddress", "127.0.0.2");
        context.setInitParameter("apiml.discoveryService.port", "10011");
        context.setInitParameter("apiml.discoveryService.hostname", "localhost");
        context.setInitParameter("apiml.ssl.verifySslCertificatesOfServices", "true");
        context.setInitParameter("apiml.ssl.keyPassword", "password123");
        context.setInitParameter("apiml.ssl.keyStorePassword", "password");
        context.setInitParameter("apiml.ssl.trustStore", "../keystore/localhost/localhost.keystore.p12");
        context.setInitParameter("apiml.ssl.trustStorePassword", "password");

        return context;
    }
}
