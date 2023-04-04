/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.gateway.security.mapping;

import org.springframework.security.access.AccessDeniedException;

public class ExternalMapperException extends AccessDeniedException {

    private static final long serialVersionUID = 5147129144299104838L;

    public ExternalMapperException(String msg) {
        super(msg);
    }

    public ExternalMapperException(String msg, Throwable cause) {
        super(msg, cause);
    }

}
