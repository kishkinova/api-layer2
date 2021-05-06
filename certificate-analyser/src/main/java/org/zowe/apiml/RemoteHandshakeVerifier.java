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

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public class RemoteHandshakeVerifier extends HandshakeVerifier {

    private VerifierSSLContext verifierSslContext;

    @Override
    public VerifierSSLContext getVerifierSslContext() {
        return verifierSslContext;
    }

    public RemoteHandshakeVerifier(VerifierSSLContext verifierSslContext) {
        this.verifierSslContext = verifierSslContext;
    }

    public void verify() {
        String serviceAddress = verifierSslContext.getStores().getConf().getRemoteUrl();
        try {
            executeCall(new URL(serviceAddress));
        } catch (MalformedURLException e) {
            System.out.println("Incorrect url " + serviceAddress + " Error message: " + e.getMessage());
        } catch (
            SSLHandshakeException e) {
            System.out.println("Certificate at " + serviceAddress +
                " is not trusted. Please add CA of this certificate to your truststore." + getVerifierSslContext().getStores().getConf().getTrustStore());
        } catch (
            IOException e) {
            System.out.println("Error calling endpoint " + e.getMessage());
        }
    }


}
