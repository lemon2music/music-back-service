package com.lemon.music.musicbackservice.iap.support;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/** 测试专用：生成 IAP 风格的 ES256 证书链（leaf 含华为叶子 OID）并签发 JWS。 */
public final class TestCertFactory {

    /** 华为 IAP 叶子证书标识 OID。 */
    public static final String LEAF_OID = "1.3.6.1.4.1.2011.2.415.1.1";
    private static final String SIGN_ALG = "SHA256withECDSA";

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public final X509Certificate root;
    public final X509Certificate intermediate;
    public final X509Certificate leaf;
    public final KeyPair leafKeyPair;

    public TestCertFactory() throws Exception {
        KeyPair rootKp = ecKeyPair();
        KeyPair intKp = ecKeyPair();
        leafKeyPair = ecKeyPair();
        root = issue("CN=IapTestRoot", rootKp, "CN=IapTestRoot", rootKp, true, null);
        intermediate = issue("CN=IapTestIntermediate", intKp, "CN=IapTestRoot", rootKp, true, null);
        leaf = issue("CN=IapTestLeaf", leafKeyPair, "CN=IapTestIntermediate", intKp, false, LEAF_OID);
    }

    private KeyPair ecKeyPair() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(256);
        return g.generateKeyPair();
    }

    private X509Certificate issue(String subject, KeyPair subjectKp, String issuerName,
                                  KeyPair issuerKp, boolean ca, String oid) throws Exception {
        Instant now = Instant.now();
        ContentSigner signer = new JcaContentSignerBuilder(SIGN_ALG).build(issuerKp.getPrivate());
        JcaX509v3CertificateBuilder b = new JcaX509v3CertificateBuilder(
                new X500Name(issuerName),
                BigInteger.valueOf(System.nanoTime()),
                Date.from(now.minus(1, ChronoUnit.DAYS)),
                Date.from(now.plus(365, ChronoUnit.DAYS)),
                new X500Name(subject),
                subjectKp.getPublic());
        b.addExtension(Extension.basicConstraints, true, new BasicConstraints(ca));
        if (oid != null) {
            b.addExtension(new ASN1ObjectIdentifier(oid), false, DERNull.INSTANCE);
        }
        return new JcaX509CertificateConverter().getCertificate(b.build(signer));
    }

    /** x5c 数组顺序：[leaf, intermediate, root]，用于构造 JWS header。 */
    public String[] x5cChain() throws CertificateEncodingException {
        return new String[]{
                b64(leaf.getEncoded()),
                b64(intermediate.getEncoded()),
                b64(root.getEncoded())
        };
    }

    /** 用 leaf 私钥签发 JWS（header 带 x5c，payload 任意 JSON）。 */
    public String signJws(String payloadJson) throws Exception {
        Map<String, Object> payload = new ObjectMapper().readValue(payloadJson, new TypeReference<>() {});
        return JWT.create()
                .withHeader(Map.of("alg", "ES256", "typ", "JWT", "x5c", x5cChain()))
                .withPayload(payload)
                .sign(Algorithm.ECDSA256((ECPrivateKey) leafKeyPair.getPrivate()));
    }

    private static String b64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }
}
