/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.client.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.zowe.apiml.client.services.AparBasedService;

import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
class JwtControllerTest {
    private static final ResponseEntity<?> DEFAULT_RESPONSE = new ResponseEntity<>(HttpStatus.OK);

    private MockMvc mockMvc;

    @Mock
    private AparBasedService aparService;

    @BeforeEach
    void setUp() {
        JwtController jwtController = new JwtController(aparService);
        mockMvc = MockMvcBuilders.standaloneSetup(jwtController).build();
    }

    @Test
    void whenCallZosmfBuilderEndpointWithGet_thenReturnOk() throws Exception {
        doReturn(DEFAULT_RESPONSE).when(aparService).process(any(), any(), any(), any());
        mockMvc.perform(get("/jwt/ibm/api/zOSMFBuilder")).andExpect(status().is(SC_OK));
    }
}
