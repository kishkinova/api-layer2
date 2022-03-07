package org.zowe.apiml.gateway.security.service.schema.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.zowe.apiml.security.common.token.TokenNotValidException;

public class AuthSourceTest {
    @Test
    void testSource() {
        assertEquals(AuthSource.Origin.ZOSMF, AuthSource.Origin.valueByIssuer("zosmf"));
        assertEquals(AuthSource.Origin.ZOSMF, AuthSource.Origin.valueByIssuer("zOSMF"));
        assertEquals(AuthSource.Origin.ZOWE, AuthSource.Origin.valueByIssuer("Zowe"));
        assertEquals(AuthSource.Origin.ZOWE, AuthSource.Origin.valueByIssuer("ZOWE"));
        assertEquals(AuthSource.Origin.X509, AuthSource.Origin.valueByIssuer("x509"));
        assertEquals(AuthSource.Origin.X509, AuthSource.Origin.valueByIssuer("X509"));

        Exception tnve = assertThrows(TokenNotValidException.class, () -> AuthSource.Origin.valueByIssuer(null));
        assertEquals("Unknown authentication source type : null", tnve.getMessage());

        tnve = assertThrows(TokenNotValidException.class, () -> AuthSource.Origin.valueByIssuer("unknown"));
        assertEquals("Unknown authentication source type : unknown", tnve.getMessage());
    }
}
