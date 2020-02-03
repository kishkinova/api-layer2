package org.zowe.apiml.security.common.auth;/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

import org.junit.Test;

import static org.junit.Assert.*;

public class AuthenticationSchemeTest {

    @Test
    public void testFromScheme() {
        for (AuthenticationScheme as : AuthenticationScheme.values()) {
            AuthenticationScheme as2 = AuthenticationScheme.fromScheme(as.getScheme());
            assertSame(as, as2);
        }
        assertNull(AuthenticationScheme.fromScheme("absolute nonsense"));
        assertEquals("bypass", AuthenticationScheme.fromScheme("bypass").toString());
    }

}
