package com.lemon.music.musicbackservice.iap.support;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.cert.TrustAnchor;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwsVerifierTest {

    private static TestCertFactory factory;
    private static Set<TrustAnchor> anchors;

    @BeforeAll
    static void setup() throws Exception {
        factory = new TestCertFactory();
        anchors = Set.of(new TrustAnchor(factory.root, null));
    }

    @Test
    void verifiesValidJwsAndDecodesPayload() throws Exception {
        String payload = "{\"notificationType\":\"REVOKE\"}";
        String jws = factory.signJws(payload);

        String decoded = JwsVerifier.verify(jws, anchors);

        assertThat(decoded).contains("\"notificationType\":\"REVOKE\"");
    }

    @Test
    void rejectsTamperedJws() throws Exception {
        String jws = factory.signJws("{\"a\":1}");
        String tampered = jws.substring(0, jws.length() - 5) + "AAAAA";

        assertThatThrownBy(() -> JwsVerifier.verify(tampered, anchors))
                .isInstanceOf(Exception.class);
    }

    @Test
    void rejectsEmptyJws() {
        assertThatThrownBy(() -> JwsVerifier.verify("", anchors))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
