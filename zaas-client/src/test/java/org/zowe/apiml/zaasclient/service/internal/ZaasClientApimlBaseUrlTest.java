/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.zaasclient.service.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.zowe.apiml.zaasclient.config.ConfigProperties;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class ZaasClientApimlBaseUrlTest {

    private static final String ZOWE_V2_BASE_URL = "/gateway/api/v1/auth";

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/gateway/auth", "api/v1/gateway/auth", "/gateway/api/v1/auth", "gateway/api/v1/auth"})
    void givenBaseUrl_thenTransformToOrDontChangeZoweV2BaseUrl(String baseUrl) {
        ConfigProperties configProperties = new ConfigProperties();
        configProperties.setApimlBaseUrl(baseUrl);
        assertThat(configProperties.getApimlBaseUrl(), is(ZOWE_V2_BASE_URL));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/zaasClient/auth", "api.v1.zaasClient.auth"})
    void givenBaseUrl_thenDontChangeBaseUrl(String baseUrl) {
        ConfigProperties configProperties = new ConfigProperties();
        configProperties.setApimlBaseUrl(baseUrl);
        assertThat(configProperties.getApimlBaseUrl(), is(baseUrl));
    }

    @Test
    void givenBaseUrlIsNull_thenTransformToZoweV2BaseUrl() {
        ConfigProperties configProperties = new ConfigProperties();
        configProperties.setApimlBaseUrl(null);
        assertThat(configProperties.getApimlBaseUrl(), is(ZOWE_V2_BASE_URL));
    }
}
