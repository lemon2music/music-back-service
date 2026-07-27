package com.lemon.music.musicbackservice.iap.support;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXCertPathValidatorResult;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/** JWS 验签：校验 ES256 + x5c 证书链（PKIX）+ 叶子证书 OID，返回 payload JSON。 */
@Slf4j
@Component
public class JwsVerifier {

    private static final String HEADER_X5C = "x5c";
    private static final String ALG_ES256 = "ES256";
    private static final int X5C_CHAIN_LENGTH = 3;
    private static final String LEAF_CERT_OID = "1.3.6.1.4.1.2011.2.415.1.1";

    private final Set<TrustAnchor> trustAnchors;

    public JwsVerifier(IapProperties properties) {
        this.trustAnchors = loadRootCa(properties.rootCaCertPath());
    }

    /** 生产入口：用配置的根证书验签。 */
    public String checkAndDecode(String jws) throws Exception {
        return verify(jws, trustAnchors);
    }

    /** 纯逻辑验签（供测试直接传入 anchor）。 */
    public static String verify(String jws, Set<TrustAnchor> anchors) throws Exception {
        if (jws == null || jws.isEmpty()) {
            throw new IllegalArgumentException("jws 为空");
        }
        DecodedJWT decoded = JWT.decode(jws);
        if (!ALG_ES256.equals(decoded.getAlgorithm())) {
            throw new IllegalArgumentException("alg 必须为 ES256");
        }
        String[] x5c = decoded.getHeaderClaim(HEADER_X5C).asArray(String.class);
        if (x5c == null) {
            throw new IllegalArgumentException("x5c 证书链为空");
        }
        PublicKey publicKey = verifyChainAndGetPubKey(x5c, anchors);
        JWTVerifier verifier = JWT.require(Algorithm.ECDSA256((ECPublicKey) publicKey)).build();
        verifier.verify(decoded);
        return new String(Base64.getUrlDecoder().decode(decoded.getPayload()), StandardCharsets.UTF_8);
    }

    private static PublicKey verifyChainAndGetPubKey(String[] certs, Set<TrustAnchor> anchors) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        List<Certificate> list = new LinkedList<>();
        for (String c : certs) {
            list.add(cf.generateCertificate(new ByteArrayInputStream(Base64.getDecoder().decode(c))));
        }
        if (list.size() != X5C_CHAIN_LENGTH) {
            throw new IllegalArgumentException("证书链长度必须为 3");
        }
        PKIXParameters params = new PKIXParameters(anchors);
        params.setRevocationEnabled(false);
        java.security.cert.CertPath path = cf.generateCertPath(list.subList(0, X5C_CHAIN_LENGTH - 1));
        PKIXCertPathValidatorResult result = (PKIXCertPathValidatorResult)
                java.security.cert.CertPathValidator.getInstance("PKIX").validate(path, params);

        X509Certificate leaf = (X509Certificate) list.get(0);
        if (leaf.getNonCriticalExtensionOIDs() == null
                || !leaf.getNonCriticalExtensionOIDs().contains(LEAF_CERT_OID)) {
            throw new IllegalArgumentException("叶子证书缺少 IAP OID");
        }
        return result.getPublicKey();
    }

    private static Set<TrustAnchor> loadRootCa(String path) {
        Set<TrustAnchor> anchors = new HashSet<>();
        if (path == null || path.isBlank()) {
            return anchors;
        }
        try {
            byte[] der;
            if (path.startsWith("classpath:")) {
                String res = path.substring("classpath:".length());
                der = JwsVerifier.class.getClassLoader().getResourceAsStream(res).readAllBytes();
            } else {
                der = Files.readAllBytes(Paths.get(path));
            }
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate root = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(der));
            anchors.add(new TrustAnchor(root, null));
        } catch (Exception e) {
            log.warn("加载 IAP 根证书失败（部署前须配置 app.iap.root-ca-cert-path）: {}", path);
        }
        return anchors;
    }
}
