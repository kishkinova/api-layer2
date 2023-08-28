/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.security.common.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.zowe.apiml.security.common.utils.X509Utils;

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CertificateValidatorTest {

    private static final String URL_PROVIDE_TRUSTED_CERTS = "trusted_certs_url";
    private static final String URL_WITH_NO_TRUSTED_CERTS = "invalid_url_for_trusted_certs";
    private static final X509Certificate cert1 = X509Utils.getCertificate(X509Utils.correctBase64("correct_certificate_1"));
    private static final X509Certificate cert2 = X509Utils.getCertificate(X509Utils.correctBase64("correct_certificate_2"));
    private static final X509Certificate cert3 = X509Utils.getCertificate(X509Utils.correctBase64("correct_certificate_3"));
    private CertificateValidator certificateValidator;

    @BeforeEach
    void setUp() {
        List<Certificate> trustedCerts = new ArrayList<>();
        trustedCerts.add(cert1);
        trustedCerts.add(cert2);
        TrustedCertificatesProvider mockProvider = mock(TrustedCertificatesProvider.class);
        when(mockProvider.getTrustedCerts(URL_PROVIDE_TRUSTED_CERTS)).thenReturn(trustedCerts);
        when(mockProvider.getTrustedCerts(URL_WITH_NO_TRUSTED_CERTS)).thenReturn(Collections.emptyList());
        certificateValidator = new CertificateValidator(mockProvider);
    }

    @Nested
    class WhenTrustedCertsProvided {

        @BeforeEach
        void setUp() {
            ReflectionTestUtils.setField(certificateValidator, "proxyCertificatesEndpoint", URL_PROVIDE_TRUSTED_CERTS, String.class);
        }
        @Test
        void whenAllCertificatesFoundThenTheyAreTrusted() {
            assertTrue(certificateValidator.isTrusted(new X509Certificate[]{cert1}));
            assertTrue(certificateValidator.isTrusted(new X509Certificate[]{cert2}));
            assertTrue(certificateValidator.isTrusted(new X509Certificate[]{cert1, cert2}));
        }

        @Test
        void whenSomeCertificateNotFoundThenAllUntrusted() {
            assertFalse(certificateValidator.isTrusted(new X509Certificate[]{cert3}));
            assertFalse(certificateValidator.isTrusted(new X509Certificate[]{cert1, cert3}));
            assertFalse(certificateValidator.isTrusted(new X509Certificate[]{cert2, cert3}));
        }
    }

    @Nested
    class WhenNoTrustedCertsProvided {

        @BeforeEach
        void setUp() {
            ReflectionTestUtils.setField(certificateValidator, "proxyCertificatesEndpoint", URL_WITH_NO_TRUSTED_CERTS, String.class);
        }
        @Test
        void thenAnyCertificateIsNotTrusted() {
            assertFalse(certificateValidator.isTrusted(new X509Certificate[]{cert1}));
            assertFalse(certificateValidator.isTrusted(new X509Certificate[]{cert2}));
            assertFalse(certificateValidator.isTrusted(new X509Certificate[]{cert3}));
        }
    }
}
