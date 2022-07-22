/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.gateway.security.service.schema;

import com.netflix.zuul.context.RequestContext;
import org.zowe.apiml.util.CookieUtil;

public abstract class JwtCommand extends AuthenticationCommand {

    public static final String COOKIE_HEADER = "cookie";

    public static void setCookie(RequestContext context, String name, String value) {
        context.addZuulRequestHeader(COOKIE_HEADER,
            CookieUtil.setCookie(
                context.getRequest().getHeader(COOKIE_HEADER),
                name,
                value
            )
        );
    }

    public static void removeCookie(RequestContext context, String[] names) {
        String cookie = context.getRequest().getHeader(COOKIE_HEADER);
        for (String name : names) {
            cookie = CookieUtil.removeCookie(
                cookie,
                name
            );
        }
        context.addZuulRequestHeader(COOKIE_HEADER,
            cookie
        );

    }

    @Override
    public boolean isExpired() {
        if (getExpireAt() == null) return false;

        return System.currentTimeMillis() > getExpireAt();
    }

    @Override
    public boolean isRequiredValidSource() {
        return true;
    }

    public abstract Long getExpireAt();
}
