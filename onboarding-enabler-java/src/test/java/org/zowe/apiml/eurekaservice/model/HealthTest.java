/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.eurekaservice.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthTest {
    @Test
    void healthConstructorTest() {
        final String status = "UP";

        Health health = new Health(status);

        assertEquals(health.getStatus(), status);
    }

    @Test
    void healthSetterTest() {
        final String status = "UP";
        Health health = new Health(null);
        health.setStatus(status);

        assertEquals(health.getStatus(), status);
    }
}
