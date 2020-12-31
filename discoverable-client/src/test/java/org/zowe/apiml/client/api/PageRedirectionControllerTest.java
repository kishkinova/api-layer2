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

import org.zowe.apiml.client.configuration.ApplicationConfiguration;
import org.zowe.apiml.client.configuration.SpringComponentsConfiguration;
import org.zowe.apiml.client.model.RedirectLocation;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebMvcTest(controllers = {PageRedirectionController.class}, secure = false)
@Import(value = {SpringComponentsConfiguration.class, ApplicationConfiguration.class})
class PageRedirectionControllerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @Autowired
    private MockMvc mockMvc;

    @Test
    void redirectToLocation() throws Exception {
        RedirectLocation redirectLocation = new RedirectLocation("https://hostA:8080/some/path");
        String payload = mapper.writeValueAsString(redirectLocation);

        this.mockMvc.perform(
            post("/api/v1/redirect")
                .contentType(MediaType.APPLICATION_JSON_UTF8)
                .content(payload))
            .andExpect(status().isTemporaryRedirect())
            .andExpect(redirectedUrl(redirectLocation.getLocation()));
    }

}
