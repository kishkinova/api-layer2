/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.client.impl;

import org.zowe.apiml.client.exception.PetNotFoundException;
import org.zowe.apiml.client.model.Pet;
import org.zowe.apiml.client.service.impl.PetServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;

class PetServiceImplTest {
    @Test
    void testPetSave() {
        PetServiceImpl petService = new PetServiceImpl();
        Long id = 1L;
        String name = "Falco";
        Pet pet = new Pet(id, name);

        Pet savedPet = petService.save(pet);

        assertThat(savedPet.getId(), is(id));
        assertThat(savedPet.getName(), is(name));
    }

    @Test
    void testGetPetById() {
        PetServiceImpl petService = new PetServiceImpl();
        petService.init();
        Long id = 1L;
        String name = "Falco";

        Pet pet = petService.getById(id);

        assertThat(pet.getName(), is(name));
    }

    @Test
    void testGetAll() {
        PetServiceImpl petService = new PetServiceImpl();
        petService.init();
        int expectedSize = 4;

        List<Pet> pets = petService.getAll();

        assertThat(pets.size(), is(expectedSize));
    }

    @Test
    void testUpdateNotExistingPet() {
        Pet pet = new Pet(404L, "Not Existing");
        PetServiceImpl petService = new PetServiceImpl();

        Pet updatedPet = petService.update(pet);

        assertNull(updatedPet);
    }

    @Test
    void testUpdateOfExistingPEt() {
        String name = "Big Falco";
        Pet pet = new Pet(1L, name);
        PetServiceImpl petService = new PetServiceImpl();
        petService.init();

        Pet updatedPet = petService.update(pet);

        assertThat(updatedPet.getName(), is(name));
    }

    @Test
    void testDeleteNotExistingPet() {
        Long id = 404L;
        PetServiceImpl petService = new PetServiceImpl();

        assertThrows(PetNotFoundException.class, () -> petService.deleteById(id));
    }

    @Test
    void testDeleteExistingPet() {
        Long id = 1L;
        int expectedSize = 3;
        PetServiceImpl petService = new PetServiceImpl();
        petService.init();

        petService.deleteById(id);

        assertThat(petService.getAll().size(), is(expectedSize));
    }

}
