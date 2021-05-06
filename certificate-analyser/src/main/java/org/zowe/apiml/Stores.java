/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

public class Stores {

    public static final String SAFKEYRING = "safkeyring";
    private KeyStore keyStore;
    private KeyStore trustStore;
    private final ApimlConf conf;

    public Stores(ApimlConf conf) {
        this.conf = conf;
        init(conf);
    }

    void init(ApimlConf conf) {
        if (conf.getKeyStore().startsWith(SAFKEYRING)) {
            try (InputStream keyringIStream = keyRingUrl(conf.getKeyStore()).openStream()) {
                this.keyStore = readKeyStore(keyringIStream, conf.getKeyPasswd().toCharArray(), conf.getKeyStoreType());
                this.trustStore = this.keyStore;
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        } else {
            try (InputStream keyStoreIStream = new FileInputStream(conf.getKeyStore());
                 InputStream trustStoreIStream = new FileInputStream(conf.getTrustStore())) {
                this.keyStore = readKeyStore(keyStoreIStream, conf.getKeyPasswd().toCharArray(), conf.getKeyStoreType());
                this.trustStore = readKeyStore(trustStoreIStream, conf.getTrustPasswd().toCharArray(), conf.getTrustStoreType());
            } catch (FileNotFoundException e) {
                System.err.println("Error while loading keystore file. Error message: " + e.getMessage() + "\n" +
                    "Possible solution: Verify correct path to the keystore. Change owner or permission to the keystore file.");
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }
    }

    public static KeyStore readKeyStore(InputStream is, char[] pass, String type) throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(is, pass);
        return keyStore;
    }

    public KeyStore getKeyStore() {
        return keyStore;
    }

    public KeyStore getTrustStore() {
        return trustStore;
    }

    public ApimlConf getConf() {
        return conf;
    }

    public static URL keyRingUrl(String uri) throws MalformedURLException {
        if (!uri.startsWith(SAFKEYRING + ":////")) {
            System.out.println("Incorrect key ring format: " + uri
                + ". Make sure you use format safkeyring:////userId/keyRing");
        }

        return new URL(replaceFourSlashes(uri));

    }

    public static String replaceFourSlashes(String storeUri) {
        return storeUri == null ? null : storeUri.replaceFirst("////", "//");
    }
}
