/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.gateway.security.login.zosmf;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.text.ParseException;
import java.util.Base64;
import java.util.Date;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public class JwkToPublicKeyConverter {

    /**
     * Converts the first public key in JWK in JSON to a certificate PEM format. The
     * public key is from JWK but the rest is fake.
     */
    public String convertFirstPublicKeyJwkToPem(String jwkJson) {
        try {
            String publicKeyPem = convertFirstPublicKeyJwkToPublicKeyPem(jwkJson);
            PEMParser pemParser = new PEMParser(new StringReader(publicKeyPem));
            Object publicKey = pemParser.readObject();

            PrivateKey privateKey = generatePrivateKey();

            ContentSigner signer = new JcaContentSignerBuilder("SHA256with" + privateKey.getAlgorithm())
                    .build(privateKey);
            X509CertificateHolder x509CertificateHolder = new X509v3CertificateBuilder(new X500Name("CN=Zowe"),
                    new BigInteger("0"), new Date(), new Date(), new X500Name("CN=Zowe"),
                    (SubjectPublicKeyInfo) publicKey).build(signer);

            return certificateHolderToPem(x509CertificateHolder);
        } catch (ParseException | JOSEException | CertificateException | IOException | OperatorCreationException
                | NoSuchAlgorithmException e) {
            throw new JwkConversionError(e);
        }
    }

    private String certificateHolderToPem(X509CertificateHolder x509CertificateHolder)
            throws CertificateException, IOException {
        StringWriter sw = new StringWriter();
        Certificate cert = CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(x509CertificateHolder.getEncoded()));
        JcaPEMWriter pemWriter = new JcaPEMWriter(sw);
        pemWriter.writeObject(cert);
        pemWriter.flush();
        return sw.toString();
    }

    private PrivateKey generatePrivateKey() throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048, new SecureRandom(new byte[] { 0 }));
        KeyPair kp = kpg.generateKeyPair();
        PrivateKey pvt = kp.getPrivate();
        return pvt;
    }

    String convertFirstPublicKeyJwkToPublicKeyPem(String jwkJson) throws JOSEException, ParseException {
        PublicKey key = JWKSet.parse(jwkJson).toPublicJWKSet().getKeys().get(0).toRSAKey().toPublicKey();
        String encoded = Base64.getEncoder().encodeToString(key.getEncoded());
        StringBuilder s = new StringBuilder();
        s.append("-----BEGIN PUBLIC KEY-----");
        for (int i = 0; i < encoded.length(); i++) {
            if (((i % 64) == 0) && (i != (encoded.length() - 1))) {
                s.append("\n");
            }
            s.append(encoded.charAt(i));
        }
        s.append("\n");
        s.append("-----END PUBLIC KEY-----\n");
        String pem = s.toString();
        return pem;
    }
}
