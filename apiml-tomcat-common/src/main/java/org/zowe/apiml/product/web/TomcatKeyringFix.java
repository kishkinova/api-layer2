/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.product.web;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TomcatKeyringFix implements TomcatConnectorCustomizer {

    private static final Pattern KEYRING_PATTERN = Pattern.compile("^safkeyring[^:]*[:][/]{2,4}[^/].*$");

    @Value("${server.ssl.keyStore:#{null}}")
    private String keyStore;

    @Value("${server.ssl.trustStore:#{null}}")
    private String trustStore;

    boolean isKeyring(String input) {
        if (input == null) return false;
        Matcher matcher = KEYRING_PATTERN.matcher(input);
        return matcher.matches();
    }

    @Override
    public void customize(Connector connector) {
        Arrays.stream(connector.findSslHostConfigs()).forEach(sslConfig -> {
            if (isKeyring(keyStore)) {
                sslConfig.setCertificateKeystoreFile(keyStore);
            }

            if (isKeyring(trustStore)) {
                sslConfig.setTruststoreFile(trustStore);
            }
        });
    }

}
