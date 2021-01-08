/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.caching.service.inmemory.config;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.zowe.apiml.caching.config.GeneralConfig;

@Configuration
@Data
@ToString
@RequiredArgsConstructor
public class InMemoryConfig {
    private final GeneralConfig generalConfig;

    @Value("${caching.storage.inmemory.size:10}")
    private int maxDataSize;

}
