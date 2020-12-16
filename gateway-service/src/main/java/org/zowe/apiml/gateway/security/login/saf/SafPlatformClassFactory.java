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

import org.zowe.apiml.message.log.ApimlLogger;
import org.zowe.apiml.message.yaml.YamlMessageServiceInstance;
import org.zowe.apiml.product.logging.annotations.InjectApimlLogger;
import org.zowe.apiml.util.ClassOrDefaultProxyUtils;

public class SafPlatformClassFactory implements PlatformClassFactory {
    @InjectApimlLogger
    private final ApimlLogger apimlLog = ApimlLogger.of(SafPlatformClassFactory.class, YamlMessageServiceInstance.getInstance());

    @Override
    public Class<?> getPlatformUserClass() {
        Object platformUser = getPlatformUser();
        if (!((ClassOrDefaultProxyUtils.ClassOrDefaultProxyState) platformUser).isUsingBaseImplementation()) {
            apimlLog.log("org.zowe.apiml.security.loginEndpointInDummyMode", MockPlatformUser.VALID_USERID, MockPlatformUser.VALID_PASSWORD);
        }
        return platformUser.getClass();
    }

    @Override
    public Class<?> getPlatformReturnedClass() throws ClassNotFoundException {
        Class<?> aClass;
        try {
            aClass = Class.forName("com.ibm.os390.security.PlatformReturned");
        } catch (ClassNotFoundException e) {
            aClass = Class.forName("org.zowe.apiml.security.common.auth.saf.PlatformReturned");
        }
        return aClass;
    }

    @Override
    public Object getPlatformUser() {
        return ClassOrDefaultProxyUtils.createProxy(PlatformUser.class, "com.ibm.os390.security.PlatformUser", MockPlatformUser::new);
    }
}
