/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.client.services.versions;

import org.zowe.apiml.client.services.apars.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AvailableApars {
    private final Map<String, Apar> implementedApars = new HashMap<>();

    public AvailableApars(List<String> usernames, List<String> passwords, String jwtKeystorePath, Integer timeout) {
        implementedApars.put("PH12143", new PH12143(usernames, passwords, jwtKeystorePath, timeout));
        implementedApars.put("PH17867", new NoApar());
        implementedApars.put("PH28507", new NoApar());
        implementedApars.put("PH28532", new NoApar());
        implementedApars.put("PH34201", new PH34201(usernames, passwords, jwtKeystorePath, timeout));
        implementedApars.put("PH34912", new PH34912(usernames, passwords, jwtKeystorePath, timeout));
        implementedApars.put("RSU2012", new RSU2012(usernames, passwords, jwtKeystorePath, timeout));
        implementedApars.put("JwtKeys", new JwtKeys(usernames, passwords));
        implementedApars.put("AuthenticateApar", new AuthenticateApar(usernames, passwords, timeout));
    }

    public List<Apar> getApars(List<String> names) {
        ArrayList<Apar> result = new ArrayList<>();
        for (String name : names) {
            if (implementedApars.containsKey(name)) {
                result.add(implementedApars.get(name));
            }
        }

        return result;
    }
}
