/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.cloudgatewayservice.acceptance.common;

import java.util.HashMap;
import java.util.Map;

public class MetadataBuilder {
    private Map<String, String> metadata;

    public MetadataBuilder() {
        metadata = new HashMap<>();
        metadata.put("apiml.routes.gateway-url", "/");
    }


    public Map<String, String> build() {
        return metadata;
    }

    public static MetadataBuilder defaultInstance() {
        MetadataBuilder builder = new MetadataBuilder();
        return builder;
    }

    public static MetadataBuilder customInstance() {
        MetadataBuilder builder = new MetadataBuilder();
        return builder;
    }
}
