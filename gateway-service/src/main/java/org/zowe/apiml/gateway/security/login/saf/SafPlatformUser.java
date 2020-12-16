/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.gateway.security.login.saf;

import org.springframework.security.authentication.AuthenticationServiceException;
import org.zowe.apiml.security.common.auth.saf.PlatformReturned;
import org.zowe.apiml.security.common.auth.saf.PlatformReturnedHelper;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

public class SafPlatformUser implements PlatformUser {

    private final PlatformClassFactory platformClassFactory;
    private final PlatformReturnedHelper<Object> platformReturnedHelper;
    private final MethodHandle authenticateMethodHandle;

    public SafPlatformUser(PlatformClassFactory platformClassFactory)
        throws IllegalAccessException, ClassNotFoundException, NoSuchFieldException, NoSuchMethodException
    {
        this.platformClassFactory = platformClassFactory;
        this.platformReturnedHelper = new PlatformReturnedHelper<>((Class<Object>) platformClassFactory.getPlatformReturnedClass());

        Method method = platformClassFactory.getPlatformUserClass()
            .getMethod("authenticate", String.class, String.class);
        authenticateMethodHandle = MethodHandles.lookup().unreflect(method);
    }

    @Override
    public PlatformReturned authenticate(String userid, String password) {
        try {
            Object safReturned = authenticateMethodHandle.invokeWithArguments(platformClassFactory.getPlatformUser(), userid, password);
            return platformReturnedHelper.convert(safReturned);
        } catch (Throwable t) {
            throw new AuthenticationServiceException("A failure occurred when authenticating.", t);
        }
    }

}
