/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.security.client;

import org.zowe.apiml.security.client.config.SecurityServiceConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * Annotation to enable the security client and integration of the security-service-client-spring library.
 * The annotation handles necessary component scans, creates the GatewaySecurityService and starts the Gateway lookup logic.
 */

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Documented
@Import(SecurityServiceConfiguration.class)
@Configuration
public @interface EnableApimlAuth {
}
