/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.gateway.ribbon.loadbalancer.model;

import lombok.Data;

import java.util.Date;

/**
 * Data POJO that represents entry in load balancing service
 */
@Data
public class LoadBalancerCacheRecord {
    private final String instanceId;
    private final long creationTime = currentTime();

    private static long currentTime() {
        return new Date().getTime();
    }
}
