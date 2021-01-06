/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.discovery;

import org.zowe.apiml.discovery.config.EurekaConfig;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "eureka.client.fetchRegistry=false",
        "eureka.client.registerWithEureka=false"
    },
    classes = {DiscoveryServiceApplication.class, EurekaConfig.class}
)
@AutoConfigureMockMvc
class EurekaSecuredEndpointsTest {
    private static final String EUREKA_ENDPOINT = "/eureka/apps";

    private String eurekaUserName = "eureka";
    private String eurekaUserPassword = "password";

    @Autowired
    private MockMvc mvc;

    @Test
    void shouldAllowCallForEurekaUser() throws Exception {
        String basicToken = "Basic " + Base64.getEncoder().encodeToString((eurekaUserName + ":" + eurekaUserPassword).getBytes());
        mvc.perform(get(EUREKA_ENDPOINT)
            .header("Authorization", basicToken)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }

    @Test
    void shouldForbidCallForNotEurekaUser() throws Exception {
        mvc.perform(get(EUREKA_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }

}
