/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package com.ca.mfaas.apicatalog.services.cached.model;

import com.ca.mfaas.config.ApiInfo;
import com.ca.mfaas.product.routing.RoutedServices;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Contains all necessary information to create API Documentation
 * running through the Gateway
 */
@Data
@AllArgsConstructor
public class ApiDocInfo {
    ApiInfo apiInfo;
    String apiDocContent;
    RoutedServices routes;
}
