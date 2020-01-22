/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.discovery.staticdef;

import lombok.Data;

/**
 * Represents one routes subservice inside a service.
 */
@Data class Route {
    /** The beginning of the path at the gateway. */
    private String gatewayUrl;

    /** Continuation of the path at the service after the base path of the service. */
    private String serviceRelativeUrl;
}
