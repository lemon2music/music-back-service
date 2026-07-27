# 服务端 PHP 完整代码参考

所有代码来自华为官方demo: https://gitcode.com/HarmonyOS_Samples/iapkit-sample-serverdemo/tree/PHP

## 项目结构

```
src/main/php/com/huawei/iap/server/demo/
  APIExample.php            # 入口示例
  IAPServer.php             # HTTP客户端基类
  JWTGenerator.php          # JWT生成器
  JWSChecker.php            # JWS验签
  OrderService.php          # 订单服务
  SubscriptionService.php   # 订阅服务
  notification/
    AppServer.php           # 通知处理（含通知常量定义）
```

## 依赖

```
composer require lcobucci/jwt
composer require phpseclib/phpseclib:~3.0
```

## IAPServer.php - HTTP客户端基类

```php
<?php

class IapServer {
    const URL_ROOT = "https://iap.cloud.huawei.com";
    const TIMEOUT_SECOND = 5;
    const WHITE_SPACE = " ";

    public function httpPost($url, $bodyDict) {
        $jsonData = json_encode($bodyDict);
        $jwt = JWTGenerator::genJwt($jsonData);
        $headers = $this->buildAuthHeaders($jwt);

        $ch = curl_init();
        curl_setopt($ch, CURLOPT_URL, $url);
        curl_setopt($ch, CURLOPT_POST, true);
        curl_setopt($ch, CURLOPT_POSTFIELDS, $jsonData);
        curl_setopt($ch, CURLOPT_HTTPHEADER, $headers);
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
        curl_setopt($ch, CURLOPT_TIMEOUT, self::TIMEOUT_SECOND);

        $response = curl_exec($ch);
        $statusCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
        curl_close($ch);

        if ($statusCode == 401) {
            // JWT认证错误
            echo "jwt authentication error: " . $response;
        } elseif ($statusCode != 200) {
            echo "request error: " . $response;
        }
        return $response;
    }

    private function buildAuthHeaders($jwt) {
        $authHead = "Bearer" . self::WHITE_SPACE . $jwt;
        return [
            "Content-Type: application/json; charset=UTF-8",
            "Authorization: " . $authHead
        ];
    }
}
```

## JWTGenerator.php - JWT生成器

```php
<?php

use Lcobucci\JWT\Configuration;
use Lcobucci\JWT\Signer\Ecdsa\Sha256;
use Lcobucci\JWT\Signer\Key\InMemory;

class JWTGenerator {
    // TODO: 替换为实际的私钥文件路径
    private static $JWT_PRI_KEY_PATH = "/path/to/key/priKey.p8";
    private static $ACTIVE_TIME_SECOND = 3600;

    // TODO: 替换为实际值
    private static $KID = "Key ID";
    private static $ISS = "Issuer ID";
    private static $AID = "App ID";

    public static function genJwt($bodyStr) {
        $privateKey = file_get_contents(self::$JWT_PRI_KEY_PATH);
        $config = Configuration::forAsymmetricSigner(
            new Sha256(),
            InMemory::plainText($privateKey),
            InMemory::empty()
        );

        $signTime = time();
        $digest = hash('sha256', $bodyStr);

        $token = $config->builder()
            ->withHeader('alg', 'ES256')
            ->withHeader('typ', 'JWT')
            ->withHeader('kid', self::$KID)
            ->withClaim('iss', self::$ISS)
            ->withClaim('aud', 'iap-v1')
            ->withClaim('iat', $signTime)
            ->withClaim('exp', $signTime + self::$ACTIVE_TIME_SECOND)
            ->withClaim('aid', self::$AID)
            ->withClaim('digest', $digest)
            ->getToken($config->signer(), $config->signingKey());

        return $token->toString();
    }
}
```

## JWSChecker.php - JWS验签

```php
<?php

use Lcobucci\JWT\Configuration;
use Lcobucci\JWT\Signer\Ecdsa\Sha256;
use Lcobucci\JWT\Signer\Key\InMemory;
use phpseclib3\File\X509;

class JWSChecker {
    // TODO: 替换为实际的华为根CA证书路径
    private static $CA_CERT_FILE_PATH = "/path/to/cer/RootCaG2Ecdsa.cer";
    private static $LEAF_CERT_OID = "1.3.6.1.4.1.2011.2.415.1.1";
    private static $X5C_CHAIN_LENGTH = 3;
    private static $CRL_SOFT_FAIL_ENABLED = false;

    public static function checkAndDecodeJWS($jwsStr) {
        if (empty($jwsStr)) {
            throw new Exception("jwsStr was null");
        }

        $parts = explode('.', $jwsStr);
        if (count($parts) !== 3) {
            throw new Exception("invalid JWS format");
        }

        $header = json_decode(base64_decode(strtr($parts[0], '-_', '+/')), true);
        $payload = json_decode(base64_decode(strtr($parts[1], '-_', '+/')), true);

        if ($header['alg'] !== 'ES256') {
            throw new Exception("alg must be ES256");
        }

        $x5cChain = $header['x5c'] ?? null;
        if ($x5cChain === null || count($x5cChain) !== self::$X5C_CHAIN_LENGTH) {
            throw new Exception("invalid x5c chain");
        }

        // 验证证书链
        $publicKey = self::verifyChainAndGetPubKey($x5cChain);

        // 使用公钥验证JWS签名
        $config = Configuration::forAsymmetricSigner(
            new Sha256(),
            InMemory::empty(),
            InMemory::plainText($publicKey)
        );
        $token = $config->parser()->parse($jwsStr);
        $config->validator()->assert($token, ...$config->validationConstraints());

        return $payload;
    }

    private static function verifyChainAndGetPubKey($certificates) {
        $rootCaCert = file_get_contents(self::$CA_CERT_FILE_PATH);

        // 验证证书链
        $leafCertPem = "-----BEGIN CERTIFICATE-----\n" . $certificates[0] . "\n-----END CERTIFICATE-----";
        $midCertPem = "-----BEGIN CERTIFICATE-----\n" . $certificates[1] . "\n-----END CERTIFICATE-----";

        $x509 = new X509();
        $x509->loadCA($rootCaCert);
        $x509->loadCA($midCertPem);
        $leafCertData = $x509->loadX509($leafCertPem);

        if (!$x509->validateSignature()) {
            throw new Exception("certificate chain validation failed");
        }

        // 检查OID
        $extensions = $leafCertData['tbsCertificate']['extensions'] ?? [];
        $oidFound = false;
        foreach ($extensions as $ext) {
            if ($ext['extnId'] === self::$LEAF_CERT_OID) {
                $oidFound = true;
                break;
            }
        }
        if (!$oidFound) {
            throw new Exception("OID not found");
        }

        // 提取公钥
        return $x509->getPublicKey()->toString('PKCS8');
    }
}
```

## OrderService.php - 订单服务

```php
<?php

class OrderService extends IapServer {
    const URL_ORDER_STATUS_QUERY = "/order/harmony/v1/application/order/status/query";
    const URL_ORDER_SHIPPED_CONFIRM = "/order/harmony/v1/application/purchase/shipped/confirm";

    /** 查询消耗型或非消耗型订单的最新状态 */
    public function orderStatusQuery($purchaseOrderId, $purchaseToken) {
        $body = ["purchaseOrderId" => $purchaseOrderId, "purchaseToken" => $purchaseToken];
        $response = $this->httpPost(self::URL_ROOT . self::URL_ORDER_STATUS_QUERY, $body);
        echo "order status query response is: " . $response . "\n";
        return $response;
    }

    /** 确认消耗型或非消耗型商品已发货 */
    public function orderShippedConfirm($purchaseOrderId, $purchaseToken) {
        $body = ["purchaseOrderId" => $purchaseOrderId, "purchaseToken" => $purchaseToken];
        $response = $this->httpPost(self::URL_ROOT . self::URL_ORDER_SHIPPED_CONFIRM, $body);
        echo "order shipped confirm response is: " . $response . "\n";
        return $response;
    }
}
```

## SubscriptionService.php - 订阅服务

```php
<?php

class SubscriptionService extends IapServer {
    const URL_SUB_STATUS_QUERY = "/subscription/harmony/v1/application/subscription/status/query";
    const URL_SUB_SHIPPED_CONFIRM = "/subscription/harmony/v1/application/purchase/shipped/confirm";

    /** 查询自动续期订阅的最新状态 */
    public function subStatusQuery($purchaseOrderId, $purchaseToken) {
        $body = ["purchaseOrderId" => $purchaseOrderId, "purchaseToken" => $purchaseToken];
        $response = $this->httpPost(self::URL_ROOT . self::URL_SUB_STATUS_QUERY, $body);
        echo "sub status query response is: " . $response . "\n";
        return $response;
    }

    /** 确认订阅商品已发货 */
    public function subShippedConfirm($purchaseOrderId, $purchaseToken) {
        $body = ["purchaseOrderId" => $purchaseOrderId, "purchaseToken" => $purchaseToken];
        $response = $this->httpPost(self::URL_ROOT . self::URL_SUB_SHIPPED_CONFIRM, $body);
        echo "sub shipped confirm response is: " . $response . "\n";
        return $response;
    }
}
```

## 通知处理 (notification/AppServer.php)

```php
<?php

require_once __DIR__ . '/../JWSChecker.php';

// 通知类型常量
class NotificationType {
    const DID_NEW_TRANSACTION = "DID_NEW_TRANSACTION";
    const DID_CHANGE_RENEWAL_STATUS = "DID_CHANGE_RENEWAL_STATUS";
    const REVOKE = "REVOKE";
    const RENEWAL_TIME_MODIFIED = "RENEWAL_TIME_MODIFIED";
    const EXPIRE = "EXPIRE";
    const SYNC = "SYNC";
}

// 子通知类型常量
class SubNotificationType {
    const INITIAL_BUY = "INITIAL_BUY";
    const DID_RENEW = "DID_RENEW";
    const RESTORE = "RESTORE";
    const AUTO_RENEW_ENABLED = "AUTO_RENEW_ENABLED";
    const AUTO_RENEW_DISABLED = "AUTO_RENEW_DISABLED";
    const DOWNGRADE = "DOWNGRADE";
    const UPGRADE = "UPGRADE";
    const REFUND_TRANSACTION = "REFUND_TRANSACTION";
    const BILLING_RETRY = "BILLING_RETRY";
    const PRICE_INCREASE = "PRICE_INCREASE";
    const BILLING_RECOVERY = "BILLING_RECOVERY";
    const PRODUCT_NOT_FOR_SALE = "PRODUCT_NOT_FOR_SALE";
    const APPLICATION_DELETE_SUBSCRIPTION_HOSTING = "APPLICATION_DELETE_SUBSCRIPTION_HOSTING";
    const RENEWAL_EXTENDED = "RENEWAL_EXTENDED";
}

/**
 * 通知元数据（强类型）
 */
class NotificationMetaData {
    public string $environment = '';
    public string $applicationId = '';
    public string $packageName = '';
    public int $type = 0;  // 商品类型: 0-消耗型, 1-非消耗型, 2-自动续期订阅, 3-非自动续期订阅
    public string $currentProductId = '';
    public string $subGroupId = '';
    public string $subGroupGenerationId = '';
    public string $subscriptionId = '';
    public string $purchaseToken = '';
    public string $purchaseOrderId = '';

    public static function fromArray(array $data): self {
        $meta = new self();
        foreach ($data as $key => $value) {
            if (property_exists($meta, $key)) {
                $meta->$key = $value;
            }
        }
        return $meta;
    }
}

/**
 * 通知载荷（强类型）
 */
class NotificationPayloadModel {
    public string $notificationType = '';
    public string $notificationSubtype = '';
    public string $notificationRequestId = '';
    public ?NotificationMetaData $notificationMetaData = null;
    public string $notificationVersion = '';
    public int $signedTime = 0;

    public static function fromArray(array $data): self {
        $payload = new self();
        foreach ($data as $key => $value) {
            if ($key === 'notificationMetaData' && is_array($value)) {
                $payload->notificationMetaData = NotificationMetaData::fromArray($value);
            } elseif (property_exists($payload, $key)) {
                $payload->$key = $value;
            }
        }
        return $payload;
    }
}

/**
 * 服务端关键事件通知处理
 *
 */
class AppServer {

    /**
     * 处理接收到的通知请求（完整端到端流程）
     * @param string $jwsNotification 请求body中的JWS字符串
     */
    public static function handleNotificationRequest(string $jwsNotification) {
        // 步骤1: JWS验签
        $payloadArray = JWSChecker::checkAndDecodeJWS($jwsNotification);

        // 步骤2: 解析为强类型
        $payload = NotificationPayloadModel::fromArray($payloadArray);
        $metaData = $payload->notificationMetaData;

        // 步骤3: 根据通知类型分发处理
        switch ($payload->notificationType) {
            case NotificationType::DID_NEW_TRANSACTION:
                // 新交易
                echo "New transaction - subtype: {$payload->notificationSubtype}, "
                   . "productId: {$metaData->currentProductId}\n";
                // TODO: 查询订单 → 验证有效性 → 发放权益 → 确认发货
                break;
            case NotificationType::DID_CHANGE_RENEWAL_STATUS:
                // 续费状态变更
                echo "Renewal status changed - subtype: {$payload->notificationSubtype}\n";
                // TODO: 更新本地订阅状态记录
                break;
            case NotificationType::REVOKE:
                // 撤销
                echo "Purchase revoked - subtype: {$payload->notificationSubtype}\n";
                // TODO: 撤回用户权益
                break;
            case NotificationType::RENEWAL_TIME_MODIFIED:
                // 续期时间变更
                echo "Renewal time modified - subtype: {$payload->notificationSubtype}\n";
                break;
            case NotificationType::EXPIRE:
                // 过期
                echo "Subscription expired - subtype: {$payload->notificationSubtype}\n";
                break;
            default:
                echo "Unknown notification type: {$payload->notificationType}\n";
                break;
        }

        // 步骤4: 按subtype细分处理
        self::dealNotificationBySubtype($payload->notificationSubtype);
    }

    /** 按subtype维度处理（向后兼容） */
    public static function dealNotificationV3($notificationPayloadStr) {
        $payload = json_decode($notificationPayloadStr, true);
        $subtype = $payload['notificationSubtype'] ?? '';
        self::dealNotificationBySubtype($subtype);
    }

    private static function dealNotificationBySubtype(string $subtype) {
        switch ($subtype) {
            case SubNotificationType::INITIAL_BUY:       // 首次购买
                break;
            case SubNotificationType::DID_RENEW:          // 续期成功
                break;
            case SubNotificationType::RESTORE:            // 恢复购买
                break;
            case SubNotificationType::AUTO_RENEW_ENABLED: // 开启自动续费
                break;
            case SubNotificationType::AUTO_RENEW_DISABLED:// 关闭自动续费
                break;
            case SubNotificationType::DOWNGRADE:          // 降级订阅
                break;
            case SubNotificationType::UPGRADE:            // 升级订阅
                break;
            case SubNotificationType::REFUND_TRANSACTION: // 退款
                break;
            case SubNotificationType::BILLING_RETRY:      // 扣费重试
                break;
            case SubNotificationType::PRICE_INCREASE:     // 价格上涨
                break;
            case SubNotificationType::BILLING_RECOVERY:   // 扣费恢复
                break;
            case SubNotificationType::PRODUCT_NOT_FOR_SALE: // 商品下架
                break;
            case SubNotificationType::APPLICATION_DELETE_SUBSCRIPTION_HOSTING: // 删除订阅托管
                break;
            case SubNotificationType::RENEWAL_EXTENDED:   // 续期延长
                break;
            default:
                break;
        }
    }
}
```

## 使用示例 (APIExample.php)

```php
<?php

require_once 'IAPServer.php';
require_once 'JWTGenerator.php';
require_once 'JWSChecker.php';
require_once 'OrderService.php';
require_once 'SubscriptionService.php';
require_once 'notification/AppServer.php';

// 1. 生成JWT
$body = json_encode(["purchaseOrderId" => "purchaseOrderId", "purchaseToken" => "purchaseToken"]);
$jwt = JWTGenerator::genJwt($body);
echo "JWT: " . $jwt . "\n";

// 2. 订单服务
$orderService = new OrderService();
$orderService->orderStatusQuery("purchaseOrderId", "purchaseToken");
$orderService->orderShippedConfirm("purchaseOrderId", "purchaseToken");

// 3. 订阅服务
$subService = new SubscriptionService();
$subService->subStatusQuery("purchaseOrderId", "purchaseToken");
$subService->subShippedConfirm("purchaseOrderId", "purchaseToken");

// 4. JWS验签
$payload = JWSChecker::checkAndDecodeJWS("jwsStr");

// 5. 处理通知（完整流程：JWS验签 + 业务分发）
$jwsNotification = "eyJ..."; // 华为IAP服务器发送的JWS字符串
AppServer::handleNotificationRequest($jwsNotification);
```
