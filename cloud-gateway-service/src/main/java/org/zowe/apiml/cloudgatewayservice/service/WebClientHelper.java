/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.cloudgatewayservice.service;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.zowe.apiml.message.log.ApimlLogger;
import org.zowe.apiml.product.logging.annotations.InjectApimlLogger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class WebClientHelper {
    @InjectApimlLogger
    private static final ApimlLogger apimlLog = ApimlLogger.empty();

    public static SslContext load(String keystorePath, char[] password) {
        File keyStoreFile = new File(keystorePath);
        if (keyStoreFile.exists()) {
            try (InputStream is = Files.newInputStream(Paths.get(keystorePath))) {
                KeyStore keyStore = KeyStore.getInstance("PKCS12");
                keyStore.load(is, password);
                return initSslContext(keyStore, password);
            } catch (Exception e) {
                log.error("Exception while creating SSL context", e);
                apimlLog.log("org.zowe.apiml.common.sslContextInitializationError", e.getMessage());
                System.exit(1);
                return null;
            }
        } else {
            throw new IllegalArgumentException("Not existing file: " + keystorePath);
        }
    }

    private static SslContext initSslContext(KeyStore keyStore, char[] password) throws NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException, SSLException {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("PKIX");
        kmf.init(keyStore, password);

        return SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .keyManager(kmf).build();
    }


}
