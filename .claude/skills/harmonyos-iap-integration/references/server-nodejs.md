# 服务端 Node.js 完整代码参考

所有代码来自华为官方demo: https://gitcode.com/HarmonyOS_Samples/iapkit-sample-serverdemo/tree/Node.js

## 项目结构

```
package.json
src/main/nodejs/com/huawei/iap/server/demo/
  main.js                  # 入口示例
  iap_server.js            # HTTP客户端基类
  jwt_generator.js         # JWT生成器
  jws_checker.js           # JWS验签
  order_service.js         # 订单服务
  subscription_service.js  # 订阅服务
  notification/
    app_server.js          # 通知处理
```

## 依赖 (package.json)

```json
{
  "dependencies": {
    "asn1js": "^3.0.5",
    "axios": "^1.7.7",
    "jose": "^2.0.7",
    "jsonwebtoken": "^9.0.2",
    "jsrsasign": "^11.1.0",
    "jws": "^4.0.0",
    "node-forge": "^1.3.1",
    "pkijs": "^3.2.4",
    "pvutils": "^1.1.3"
  }
}
```

## iap_server.js - HTTP客户端基类

```javascript
const axios = require("axios");
const jwtGenerator = require("./jwt_generator");

class IapServer {
    URL_ROOT = "https://iap.cloud.huawei.com";
    TIMEOUT = 5000;
    WHITE_SPACE = " ";

    httpPost(url, body_dict) {
        const json_data = JSON.stringify(body_dict);
        const jwt = jwtGenerator.genJwt(json_data);
        const headers = this.buildAuthHeaders(jwt);
        return this.__doPost(url, json_data, headers)
            .then(response => {
                if (response.status === 401) {
                    console.error(`jwt authentication error: ${response}`);
                } else if (response.status !== 200) {
                    console.error(`request error: ${response}`);
                }
                return response.data;
            })
            .catch(error => {
                console.error(error);
                return null;
            });
    }

    __doPost(url, data, headers) {
        const axiosInstance = axios.create({
            baseURL: this.URL_ROOT,
            timeout: this.TIMEOUT,
            headers: headers
        });
        return axiosInstance.post(url, data);
    }

    buildAuthHeaders(jwt) {
        const authHead = `Bearer${this.WHITE_SPACE}${jwt}`;
        return {
            "Content-Type": "application/json; charset=UTF-8",
            "Authorization": authHead
        };
    }
}

module.exports = IapServer;
```

## jwt_generator.js - JWT生成器

```javascript
const fs = require("fs");
const jwt = require("jsonwebtoken");
const crypto = require("crypto");

class JWTGenerator {
    // TODO: 替换为实际的私钥文件路径
    static JWT_PRI_KEY_PATH = "/path/to/key/priKey.p8";
    static private_key = fs.readFileSync(this.JWT_PRI_KEY_PATH, "utf8");
    static ACTIVE_TIME_SECOND = 3600;

    static JWT_HEADER = {
        "alg": "ES256",
        "typ": "JWT",
        "kid": "Key ID"       // TODO: 替换为实际的密钥ID
    };

    static JWT_PAYLOAD = {
        "iss": "Issuer ID",   // TODO: 替换为实际的密钥发行者ID
        "aud": "iap-v1",
        "aid": "APP ID",      // TODO: 替换为实际的应用ID
        "iat": 0,
        "exp": 0,
        "digest": ""
    };

    static createDigest(bodyStr) {
        const hash = crypto.createHash("sha256");
        hash.update(bodyStr);
        return hash.digest("hex");
    }

    static genJwt(bodyStr) {
        const jwtPayload = {...this.JWT_PAYLOAD};
        const digest = this.createDigest(bodyStr);
        const signTime = Math.floor(Date.now() / 1000);
        jwtPayload.iat = signTime;
        jwtPayload.exp = signTime + this.ACTIVE_TIME_SECOND;
        jwtPayload.digest = digest;
        return jwt.sign(jwtPayload, this.private_key, {
            algorithm: this.JWT_HEADER.alg,
            header: this.JWT_HEADER
        });
    }
}

module.exports = JWTGenerator;
```

## jws_checker.js - JWS验签

```javascript
const fs = require("fs");
const crypto = require("crypto");
const jws = require("jws");
const jsrsasign = require("jsrsasign");
const axios = require("axios");
const asn1 = require("asn1js");
const pkijs = require("pkijs");
const pvutils = require("pvutils");

class JWSChecker {
    // TODO: 替换为实际的华为根CA证书路径
    CA_CERT_FILE_PATH = "/path/to/cer/RootCaG2Ecdsa.cer";
    LEAF_CERT_OID = "1.3.6.1.4.1.2011.2.415.1.1";
    CRL_TIME_OUT = 5000;

    async checkAndDecodeJWS(jwsStr) {
        const jwsObject = jws.decode(jwsStr);
        const header = jwsObject.header;
        const certChain = header.x5c.slice(0, 2).map(
            cert => new crypto.X509Certificate(Buffer.from(cert, "base64")));
        this.checkCertChain(certChain);

        const jsrsassignX509Leaf = new jsrsasign.X509();
        jsrsassignX509Leaf.readCertHex(certChain[0].raw.toString("hex"));
        const jsrassignX509Intermediate = new jsrsasign.X509();
        jsrassignX509Intermediate.readCertHex(certChain[1].raw.toString("hex"));
        const x509s = [jsrsassignX509Leaf, jsrassignX509Intermediate];

        this.checkOID(x509s[0]);

        const checkCrlResult = await this.checkCRL(x509s);
        if (checkCrlResult) {
            jws.verify(jwsStr, header.alg, certChain[0].publicKey);
            return jwsObject.payload;
        } else {
            throw new Error("check crl Result is failure");
        }
    }

    checkCertChain(certChain) {
        const rootCaBuffer = fs.readFileSync(this.CA_CERT_FILE_PATH);
        const rootCaCert = new crypto.X509Certificate(rootCaBuffer);
        let validity = certChain[1].verify(rootCaCert.publicKey);
        validity = validity && certChain[0].verify(certChain[1].publicKey);
        if (!validity) {
            throw new Error("check cert chain is failure");
        }
    }

    checkOID(leafCert) {
        if (!(leafCert.getExtInfo(this.LEAF_CERT_OID) !== undefined)) {
            throw new Error("failed to verify the OID.");
        }
    }

    async checkCRL(x509s) {
        return Promise.all(x509s.map((x509) => {
            const config = {
                url: x509.getExtCRLDistributionPointsURI(),
                method: "get",
                responseType: "arraybuffer",
                timeout: this.CRL_TIME_OUT
            };
            return axios(config).then(response => {
                const crlBuffer = new Uint8Array(response.data).buffer;
                const asn1crl = asn1.fromBER(crlBuffer);
                const crlObject = new pkijs.CertificateRevocationList({ schema: asn1crl.result });
                if (!crlObject.revokedCertificates || crlObject.revokedCertificates.length === 0) return;
                for (const {userCertificate} of crlObject.revokedCertificates) {
                    if (x509.getSerialNumberHex() === pvutils.bufferToHexCodes(userCertificate.valueBlock.valueHex)) {
                        throw new Error("this certificate has been revoked");
                    }
                }
            }).catch(error => {
                console.error(`crl request error: ${error.message}`);
                throw error;
            });
        })).then(() => true, () => false);
    }
}

module.exports = JWSChecker;
```

## order_service.js - 订单服务

```javascript
const IapServer = require("./iap_server");

class OrderService extends IapServer {
    URL_ORDER_STATUS_QUERY = "/order/harmony/v1/application/order/status/query";
    URL_ORDER_SHIPPED_CONFIRM = "/order/harmony/v1/application/purchase/shipped/confirm";

    /** 查询消耗型或非消耗型订单的最新状态 */
    async orderStatusQuery(purchaseOrderId, purchaseToken) {
        const body = {"purchaseOrderId": purchaseOrderId, "purchaseToken": purchaseToken};
        return await this.httpPost(this.URL_ORDER_STATUS_QUERY, body);
    }

    /** 确认消耗型或非消耗型商品已发货 */
    async orderShippedConfirm(purchaseOrderId, purchaseToken) {
        const body = {"purchaseOrderId": purchaseOrderId, "purchaseToken": purchaseToken};
        return await this.httpPost(this.URL_ORDER_SHIPPED_CONFIRM, body);
    }
}

module.exports = OrderService;
```

## subscription_service.js - 订阅服务

```javascript
const IapServer = require("./iap_server");

class SubscriptionService extends IapServer {
    URL_SUB_STATUS_QUERY = "/subscription/harmony/v1/application/subscription/status/query";
    URL_SUB_SHIPPED_CONFIRM = "/subscription/harmony/v1/application/purchase/shipped/confirm";

    /** 查询自动续期订阅的最新状态 */
    async subStatusQuery(purchaseOrderId, purchaseToken) {
        const body = {"purchaseOrderId": purchaseOrderId, "purchaseToken": purchaseToken};
        return await this.httpPost(this.URL_SUB_STATUS_QUERY, body);
    }

    /** 确认订阅商品已发货 */
    async subShippedConfirm(purchaseOrderId, purchaseToken) {
        const body = {"purchaseOrderId": purchaseOrderId, "purchaseToken": purchaseToken};
        return await this.httpPost(this.URL_SUB_SHIPPED_CONFIRM, body);
    }
}

module.exports = SubscriptionService;
```

## 通知处理 (notification/app_server.js)

```javascript
const JWSChecker = require("../jws_checker");

const NotificationType = {
    DID_NEW_TRANSACTION: "DID_NEW_TRANSACTION",
    DID_CHANGE_RENEWAL_STATUS: "DID_CHANGE_RENEWAL_STATUS",
    REVOKE: "REVOKE",
    RENEWAL_TIME_MODIFIED: "RENEWAL_TIME_MODIFIED",
    EXPIRE: "EXPIRE"
};

const SubNotificationType = {
    INITIAL_BUY: "INITIAL_BUY",
    DID_RENEW: "DID_RENEW",
    RESTORE: "RESTORE",
    AUTO_RENEW_ENABLED: "AUTO_RENEW_ENABLED",
    AUTO_RENEW_DISABLED: "AUTO_RENEW_DISABLED",
    DOWNGRADE: "DOWNGRADE",
    UPGRADE: "UPGRADE",
    REFUND_TRANSACTION: "REFUND_TRANSACTION",
    BILLING_RETRY: "BILLING_RETRY",
    PRICE_INCREASE: "PRICE_INCREASE",
    BILLING_RECOVERY: "BILLING_RECOVERY",
    PRODUCT_NOT_FOR_SALE: "PRODUCT_NOT_FOR_SALE",
    APPLICATION_DELETE_SUBSCRIPTION_HOSTING: "APPLICATION_DELETE_SUBSCRIPTION_HOSTING",
    RENEWAL_EXTENDED: "RENEWAL_EXTENDED"
};

/**
 * 服务端关键事件通知处理
 *
 */
class AppServer {
    /**
     * 处理接收到的通知请求（完整端到端流程）
     * @param {string} jwsNotification - 请求body中的JWS字符串
     */
    static async handleNotificationRequest(jwsNotification) {
        // 步骤1: JWS验签
        const jwsChecker = new JWSChecker();
        const notificationPayloadStr = await jwsChecker.checkAndDecodeJWS(jwsNotification);

        // 步骤2: 解析通知载荷
        const notificationPayload = JSON.parse(notificationPayloadStr);
        const metaData = notificationPayload.notificationMetaData;
        const notificationType = notificationPayload.notificationType;
        const notificationSubtype = notificationPayload.notificationSubtype;

        // 步骤3: 根据通知类型分发处理
        switch (notificationType) {
            case NotificationType.DID_NEW_TRANSACTION:
                // 新交易: 用户完成了一笔新的购买
                console.log(`New transaction - subtype: ${notificationSubtype}, productId: ${metaData.currentProductId}`);
                // TODO: 查询订单 → 验证有效性 → 发放权益 → 确认发货
                break;
            case NotificationType.DID_CHANGE_RENEWAL_STATUS:
                // 续费状态变更
                console.log(`Renewal status changed - subtype: ${notificationSubtype}, subscriptionId: ${metaData.subscriptionId}`);
                // TODO: 更新本地订阅状态记录
                break;
            case NotificationType.REVOKE:
                // 撤销
                console.log(`Purchase revoked - subtype: ${notificationSubtype}, orderId: ${metaData.purchaseOrderId}`);
                // TODO: 撤回用户权益
                break;
            case NotificationType.RENEWAL_TIME_MODIFIED:
                // 续期时间变更
                console.log(`Renewal time modified - subtype: ${notificationSubtype}`);
                // TODO: 更新订阅到期时间
                break;
            case NotificationType.EXPIRE:
                // 过期
                console.log(`Subscription expired - subtype: ${notificationSubtype}`);
                // TODO: 标记订阅过期
                break;
            default:
                console.log(`Unknown notification type: ${notificationType}`);
                break;
        }

        // 步骤4: 按subtype维度细分处理
        AppServer.dealNotificationBySubtype(notificationSubtype, metaData);
    }

    /** 按subtype维度处理（向后兼容） */
    static dealNotificationV3(notificationPayloadStr) {
        const notificationPayload = JSON.parse(notificationPayloadStr);
        const notificationSubtype = notificationPayload["notificationSubtype"];
        const metaData = notificationPayload["notificationMetaData"];
        AppServer.dealNotificationBySubtype(notificationSubtype, metaData);
    }

    static dealNotificationBySubtype(notificationSubtype, metaData) {
        switch (notificationSubtype) {
            case SubNotificationType.INITIAL_BUY:       // 首次购买
                break;
            case SubNotificationType.DID_RENEW:          // 续期成功
                break;
            case SubNotificationType.RESTORE:            // 恢复购买
                break;
            case SubNotificationType.AUTO_RENEW_ENABLED: // 开启自动续费
                break;
            case SubNotificationType.AUTO_RENEW_DISABLED:// 关闭自动续费
                break;
            case SubNotificationType.DOWNGRADE:          // 降级订阅
                break;
            case SubNotificationType.UPGRADE:            // 升级订阅
                break;
            case SubNotificationType.REFUND_TRANSACTION: // 退款
                break;
            case SubNotificationType.BILLING_RETRY:      // 扣费重试
                break;
            case SubNotificationType.PRICE_INCREASE:     // 价格上涨
                break;
            case SubNotificationType.BILLING_RECOVERY:   // 扣费恢复
                break;
            case SubNotificationType.PRODUCT_NOT_FOR_SALE: // 商品下架
                break;
            case SubNotificationType.APPLICATION_DELETE_SUBSCRIPTION_HOSTING: // 删除订阅托管
                break;
            case SubNotificationType.RENEWAL_EXTENDED:   // 续期延长
                break;
            default:
                break;
        }
    }
}

module.exports = { AppServer, NotificationType, SubNotificationType };
```

## 使用示例 (main.js)

```javascript
const JWTGenerator = require("./jwt_generator.js");
const OrderService = require("./order_service.js");
const SubscriptionService = require("./subscription_service.js");
const JWSChecker = require("./jws_checker.js");
const { AppServer } = require("./notification/app_server.js");

// 1. 生成JWT
const body = {"purchaseOrderId": "purchaseOrderId", "purchaseToken": "purchaseToken"};
console.log(JWTGenerator.genJwt(JSON.stringify(body)));

// 2. 订单服务
const orderService = new OrderService();
orderService.orderStatusQuery("purchaseOrderId", "purchaseToken")
    .then(resp => console.log(`order status query: ${JSON.stringify(resp)}`));
orderService.orderShippedConfirm("purchaseOrderId", "purchaseToken")
    .then(resp => console.log(`order shipped confirm: ${JSON.stringify(resp)}`));

// 3. 订阅服务
const subService = new SubscriptionService();
subService.subStatusQuery("purchaseOrderId", "purchaseToken")
    .then(resp => console.log(`sub status query: ${JSON.stringify(resp)}`));
subService.subShippedConfirm("purchaseOrderId", "purchaseToken")
    .then(resp => console.log(`sub shipped confirm: ${JSON.stringify(resp)}`));

// 4. JWS验签
new JWSChecker().checkAndDecodeJWS("jwsStr").then(payload => console.log(payload));

// 5. 处理通知（完整流程：JWS验签 + 业务分发）
const jwsNotification = "eyJ..."; // 华为IAP服务器发送的JWS字符串
AppServer.handleNotificationRequest(jwsNotification)
    .then(() => console.log("notification processed"))
    .catch(err => console.error("notification error:", err));
```
