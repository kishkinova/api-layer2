/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.util.config;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Credentials {
    private String key;
    private String user;
    private String password;

    public Credentials(String user, String password) {
        this.user = user;
        this.password = password;
    }
}
