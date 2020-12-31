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
import org.zowe.apiml.client.model.Pet;
import org.zowe.apiml.client.service.PetService;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebMvcTest(controllers = {PetController.class}, secure = false)
@Import(ApplicationConfiguration.class)
class PetControllerGetOneTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PetService petService;

    @Test
    void getExistingPet() throws Exception {
        int id = 1;
        String name = "Falco";
        Pet pet = new Pet((long) id, name);
        when(petService.getById((long) id)).thenReturn(pet);

        this.mockMvc.perform(get("/api/v1/pets/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(id)))
            .andExpect(jsonPath("$.name", is(name)));
    }

    @Test
    void getNotExistingPet() throws Exception {
        int id = 404;
        String message = String.format("The pet with id '%s' is not found.", id);
        when(petService.getById((long) id)).thenReturn(null);

        this.mockMvc.perform(get("/api/v1/pets/" + id))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.messages[?(@.messageNumber == 'CSR0001E')].messageContent", hasItem(message)));
    }

    @Test
    void getPetByInvalidId() throws Exception {
        String id = "invalidvalue";
        String message = String.format("The pet id '%s' is invalid: it is not an integer.", id);

        this.mockMvc.perform(get("/api/v1/pets/" + id))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.messages[?(@.messageNumber == 'CSR0003E')].messageContent", hasItem(message)));
        verify(petService, never()).getById(any());
    }

}
