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

import org.zowe.apiml.auth.Authentication;
import org.zowe.apiml.auth.AuthenticationScheme;
import org.zowe.apiml.gateway.security.service.schema.source.AuthSource;


/**
 * This is abstract class for any processor which support service's authentication. They are called from ZUUL filters
 * to decorate request for target services.
 *
 * For each type of scheme should exist right one implementation.
 */
public interface AbstractAuthenticationScheme {

    /**
     * @return Scheme which is supported by this component
     */
    AuthenticationScheme getScheme();

    /**
     * This method decorate the request for target service
     *
     * @param authentication DTO describing details about authentication
     * @param authSource User's parsed (Zowe's) JWT token, evaluated only, if needed
     */
    AuthenticationCommand createCommand(Authentication authentication, AuthSource authSource);

    /**
     * Define implementation, which will be use in case no scheme is defined.
     *
     * @return true if this implementation is default, otherwise false
     */
    default boolean isDefault() {
        return false;
    }

}
