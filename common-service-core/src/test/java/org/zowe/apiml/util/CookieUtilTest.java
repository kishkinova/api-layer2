/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class CookieUtilTest {

    @Test
    void testSetCookie() {
        assertEquals("a=bc", CookieUtil.setCookie(null, "a", "bc"));
        assertEquals("a=bc", CookieUtil.setCookie("", "a", "bc"));
        assertEquals("a=1;b=2;c=4", CookieUtil.setCookie("a=1;c=3;b=2", "c", "4"));
        assertEquals("name=value", CookieUtil.setCookie(";", "name", "value"));
        assertEquals("name=value", CookieUtil.setCookie(";;;", "name", "value"));
        assertEquals("a=1;b=2;null=null", CookieUtil.setCookie("a=1;b=2", null, null));
    }

    @Test
    void removeCookie() {
        String c = "a=1;b=2";
        assertSame(c, CookieUtil.removeCookie(c, "c"));
        assertEquals("", CookieUtil.removeCookie("a=b", "a"));
        assertEquals("a=1;c=3", CookieUtil.removeCookie("a=1;b=2;c=3", "b"));
        assertEquals("", CookieUtil.removeCookie("a=1;a=2;a=3", "a"));
    }

}
