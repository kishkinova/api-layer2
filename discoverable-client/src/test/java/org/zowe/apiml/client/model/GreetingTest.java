/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.client.model;

import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GreetingTest {
    @Test
    void getName() {

        final Date date = new Date();
        final String name = "Dude";
        Greeting greet = new Greeting(date, name);

        String actualName = greet.getContent();
        assertEquals(name, actualName);

        Date actualDate = greet.getDate();
        assertEquals(date, actualDate);
    }

}
