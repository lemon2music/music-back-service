# 服务端 Golang 完整代码参考

所有代码来自华为官方demo: https://gitcode.com/HarmonyOS_Samples/iapkit-sample-serverdemo/tree/Golang

## 项目结构

```
go.mod
main.go
src/main/go/com/huawei/iap/server/demo/
  iap_server.go            # HTTP客户端基类
  jwt_generator.go         # JWT生成器
  jws_checker.go           # JWS验签
  order_service.go         # 订单服务
  subscription_service.go  # 订阅服务
  notification/
    appserver.go              # 通知处理
    notificationConstant.go   # 通知常量
    notificationMetaData.go   # 通知元数据
    notificationPayload.go    # 通知载荷
```

## 依赖 (go.mod)

```go
module iap-golang-sample

go 1.13

require github.com/cristalhq/jwt/v3 v3.1.0
```

## iap_server.go - HTTP客户端基类

```go
package demo

import (
    "bytes"
    "fmt"
    "io/ioutil"
    "net/http"
    "time"
)

const URLRoot = "https://iap.cloud.huawei.com"
const TimeoutSecond = 5

type IapServer struct{}

func (s *IapServer) HttpPost(url string, bodyJson string) (string, error) {
    jwtGenerator := &JWTGenerator{}
    jwtStr, err := jwtGenerator.GenJWT(bodyJson)
    if err != nil {
        return "", fmt.Errorf("generate jwt error: %v", err)
    }

    client := &http.Client{Timeout: TimeoutSecond * time.Second}
    req, err := http.NewRequest("POST", url, bytes.NewBuffer([]byte(bodyJson)))
    if err != nil {
        return "", fmt.Errorf("create request error: %v", err)
    }

    req.Header.Set("Content-Type", "application/json; charset=UTF-8")
    req.Header.Set("Authorization", BuildAuthorization(jwtStr))

    resp, err := client.Do(req)
    if err != nil {
        return "", fmt.Errorf("request error: %v", err)
    }
    defer resp.Body.Close()

    body, err := ioutil.ReadAll(resp.Body)
    if err != nil {
        return "", fmt.Errorf("read response error: %v", err)
    }

    if resp.StatusCode == http.StatusUnauthorized {
        return "", fmt.Errorf("jwt authentication error: %s", string(body))
    } else if resp.StatusCode != http.StatusOK {
        return "", fmt.Errorf("request failed: %s", string(body))
    }

    return string(body), nil
}

func BuildAuthorization(jwt string) string {
    return "Bearer " + jwt
}
```

## jwt_generator.go - JWT生成器

```go
package demo

import (
    "crypto/ecdsa"
    "crypto/sha256"
    "crypto/x509"
    "encoding/hex"
    "encoding/pem"
    "fmt"
    "io/ioutil"
    "time"

    "github.com/cristalhq/jwt/v3"
)

// TODO: 替换为实际值
const (
    JWTPriKeyPath = "/path/to/key/priKey.p8"  // 私钥文件路径
    KeyID         = "Key ID"                   // 密钥ID
    IssuerID      = "Issuer ID"                // 密钥发行者ID
    AppID         = "App ID"                   // 应用ID
    ActiveTime    = 3600                       // JWT有效期（秒）
)

type IAPJWTClaims struct {
    jwt.StandardClaims
    Iss    string `json:"iss"`
    Aud    string `json:"aud"`
    Iat    int64  `json:"iat"`
    Exp    int64  `json:"exp"`
    Aid    string `json:"aid"`
    Digest string `json:"digest"`
}

type JWTGenerator struct{}

func (g *JWTGenerator) GenJWT(bodyStr string) (string, error) {
    // 读取私钥文件
    keyData, err := ioutil.ReadFile(JWTPriKeyPath)
    if err != nil {
        return "", fmt.Errorf("read private key error: %v", err)
    }

    // 解析PEM格式的PKCS#8私钥
    block, _ := pem.Decode(keyData)
    if block == nil {
        return "", fmt.Errorf("failed to parse PEM block")
    }

    key, err := x509.ParsePKCS8PrivateKey(block.Bytes)
    if err != nil {
        return "", fmt.Errorf("parse private key error: %v", err)
    }

    ecdsaKey, ok := key.(*ecdsa.PrivateKey)
    if !ok {
        return "", fmt.Errorf("not an ECDSA private key")
    }

    // 创建ES256签名器
    signer, err := jwt.NewSignerES(jwt.ES256, ecdsaKey)
    if err != nil {
        return "", fmt.Errorf("create signer error: %v", err)
    }

    // 构建JWT Builder
    builder := jwt.NewBuilder(signer, jwt.WithKeyID(KeyID))

    // 计算请求体的SHA-256摘要
    hash := sha256.Sum256([]byte(bodyStr))
    digest := hex.EncodeToString(hash[:])

    signTime := time.Now().Unix()

    claims := &IAPJWTClaims{
        Iss:    IssuerID,
        Aud:    "iap-v1",
        Iat:    signTime,
        Exp:    signTime + ActiveTime,
        Aid:    AppID,
        Digest: digest,
    }

    token, err := builder.Build(claims)
    if err != nil {
        return "", fmt.Errorf("build token error: %v", err)
    }

    return token.String(), nil
}
```

## jws_checker.go - JWS验签

```go
package demo

import (
    "crypto/ecdsa"
    "crypto/x509"
    "encoding/base64"
    "encoding/json"
    "encoding/pem"
    "fmt"
    "io/ioutil"
    "net/http"
    "strings"
    "time"

    "github.com/cristalhq/jwt/v3"
)

// TODO: 替换为实际的华为根CA证书路径
const CACertFilePath = "/path/to/cer/RootCaG2Ecdsa.cer"
const LeafCertOID = "1.3.6.1.4.1.2011.2.415.1.1"
const CRLTimeout = 5

type JWSChecker struct{}

type JWSHeader struct {
    Alg string   `json:"alg"`
    X5c []string `json:"x5c"`
}

func (c *JWSChecker) CheckAndDecodeJWS(jwsStr string) (string, error) {
    if jwsStr == "" {
        return "", fmt.Errorf("jwsStr was null")
    }

    parts := strings.Split(jwsStr, ".")
    if len(parts) != 3 {
        return "", fmt.Errorf("invalid JWS format")
    }

    // 解析Header
    headerBytes, err := base64.RawURLEncoding.DecodeString(parts[0])
    if err != nil {
        return "", fmt.Errorf("decode header error: %v", err)
    }
    var header JWSHeader
    if err := json.Unmarshal(headerBytes, &header); err != nil {
        return "", fmt.Errorf("parse header error: %v", err)
    }

    if header.Alg != "ES256" {
        return "", fmt.Errorf("alg must be ES256")
    }
    if len(header.X5c) != 3 {
        return "", fmt.Errorf("invalid x5c chain length")
    }

    // 验证证书链并获取公钥
    publicKey, err := c.verifyChainAndGetPubKey(header.X5c)
    if err != nil {
        return "", fmt.Errorf("verify chain error: %v", err)
    }

    // 使用公钥验证JWS签名
    verifier, err := jwt.NewVerifierES(jwt.ES256, publicKey)
    if err != nil {
        return "", fmt.Errorf("create verifier error: %v", err)
    }

    token, err := jwt.ParseAndVerifyString(jwsStr, verifier)
    if err != nil {
        return "", fmt.Errorf("verify JWS error: %v", err)
    }

    return string(token.RawClaims()), nil
}

func (c *JWSChecker) verifyChainAndGetPubKey(certificates []string) (*ecdsa.PublicKey, error) {
    // 加载根CA证书
    rootCaData, err := ioutil.ReadFile(CACertFilePath)
    if err != nil {
        return nil, fmt.Errorf("read root CA error: %v", err)
    }

    rootCaBlock, _ := pem.Decode(rootCaData)
    rootCaCert, err := x509.ParseCertificate(rootCaBlock.Bytes)
    if err != nil {
        return nil, fmt.Errorf("parse root CA error: %v", err)
    }

    // 解析证书链
    certs := make([]*x509.Certificate, 3)
    for i, certB64 := range certificates {
        certDer, err := base64.StdEncoding.DecodeString(certB64)
        if err != nil {
            return nil, fmt.Errorf("decode cert %d error: %v", i, err)
        }
        cert, err := x509.ParseCertificate(certDer)
        if err != nil {
            return nil, fmt.Errorf("parse cert %d error: %v", i, err)
        }
        certs[i] = cert
    }

    // 构建证书池并验证
    roots := x509.NewCertPool()
    roots.AddCert(rootCaCert)
    intermediates := x509.NewCertPool()
    intermediates.AddCert(certs[1])

    opts := x509.VerifyOptions{
        Roots:         roots,
        Intermediates: intermediates,
        CurrentTime:   time.Now(),
    }

    if _, err := certs[0].Verify(opts); err != nil {
        return nil, fmt.Errorf("certificate verification failed: %v", err)
    }

    // 检查OID
    oidFound := false
    for _, ext := range certs[0].Extensions {
        if ext.Id.String() == LeafCertOID {
            oidFound = true
            break
        }
    }
    if !oidFound {
        return nil, fmt.Errorf("OID not found in leaf certificate")
    }

    // 检查CRL
    if err := c.checkCRL(certs[:2]); err != nil {
        return nil, fmt.Errorf("CRL check error: %v", err)
    }

    // 提取公钥
    ecdsaKey, ok := certs[0].PublicKey.(*ecdsa.PublicKey)
    if !ok {
        return nil, fmt.Errorf("not an ECDSA public key")
    }

    return ecdsaKey, nil
}

func (c *JWSChecker) checkCRL(certs []*x509.Certificate) error {
    client := &http.Client{Timeout: CRLTimeout * time.Second}

    for _, cert := range certs {
        for _, crlURL := range cert.CRLDistributionPoints {
            resp, err := client.Get(crlURL)
            if err != nil {
                // 根据安全策略决定是否忽略CRL下载异常
                return fmt.Errorf("download CRL error: %v", err)
            }
            defer resp.Body.Close()

            crlData, err := ioutil.ReadAll(resp.Body)
            if err != nil {
                return fmt.Errorf("read CRL error: %v", err)
            }

            crl, err := x509.ParseRevocationList(crlData)
            if err != nil {
                return fmt.Errorf("parse CRL error: %v", err)
            }

            for _, revokedCert := range crl.RevokedCertificateEntries {
                if revokedCert.SerialNumber.Cmp(cert.SerialNumber) == 0 {
                    return fmt.Errorf("certificate has been revoked")
                }
            }
        }
    }
    return nil
}
```

## order_service.go - 订单服务

```go
package demo

import (
    "encoding/json"
    "fmt"
)

const (
    URLOrderStatusQuery   = "/order/harmony/v1/application/order/status/query"
    URLOrderShippedConfirm = "/order/harmony/v1/application/purchase/shipped/confirm"
)

type OrderService struct {
    IapServer
}

// OrderStatusQuery 查询消耗型或非消耗型订单的最新状态
func (s *OrderService) OrderStatusQuery(purchaseOrderId, purchaseToken string) error {
    bodyMap := map[string]string{
        "purchaseOrderId": purchaseOrderId,
        "purchaseToken":   purchaseToken,
    }
    bodyJson, _ := json.Marshal(bodyMap)
    response, err := s.HttpPost(URLRoot+URLOrderStatusQuery, string(bodyJson))
    if err != nil {
        return fmt.Errorf("order status query error: %v", err)
    }
    fmt.Println("order status query response is:", response)
    return nil
}

// OrderShippedConfirm 确认消耗型或非消耗型商品已发货
func (s *OrderService) OrderShippedConfirm(purchaseOrderId, purchaseToken string) error {
    bodyMap := map[string]string{
        "purchaseOrderId": purchaseOrderId,
        "purchaseToken":   purchaseToken,
    }
    bodyJson, _ := json.Marshal(bodyMap)
    response, err := s.HttpPost(URLRoot+URLOrderShippedConfirm, string(bodyJson))
    if err != nil {
        return fmt.Errorf("order shipped confirm error: %v", err)
    }
    fmt.Println("order shipped confirm response is:", response)
    return nil
}
```

## subscription_service.go - 订阅服务

```go
package demo

import (
    "encoding/json"
    "fmt"
)

const (
    URLSubStatusQuery   = "/subscription/harmony/v1/application/subscription/status/query"
    URLSubShippedConfirm = "/subscription/harmony/v1/application/purchase/shipped/confirm"
)

type SubscriptionService struct {
    IapServer
}

// SubStatusQuery 查询自动续期订阅的最新状态
func (s *SubscriptionService) SubStatusQuery(purchaseOrderId, purchaseToken string) error {
    bodyMap := map[string]string{
        "purchaseOrderId": purchaseOrderId,
        "purchaseToken":   purchaseToken,
    }
    bodyJson, _ := json.Marshal(bodyMap)
    response, err := s.HttpPost(URLRoot+URLSubStatusQuery, string(bodyJson))
    if err != nil {
        return fmt.Errorf("sub status query error: %v", err)
    }
    fmt.Println("sub status query response is:", response)
    return nil
}

// SubShippedConfirm 确认订阅商品已发货
func (s *SubscriptionService) SubShippedConfirm(purchaseOrderId, purchaseToken string) error {
    bodyMap := map[string]string{
        "purchaseOrderId": purchaseOrderId,
        "purchaseToken":   purchaseToken,
    }
    bodyJson, _ := json.Marshal(bodyMap)
    response, err := s.HttpPost(URLRoot+URLSubShippedConfirm, string(bodyJson))
    if err != nil {
        return fmt.Errorf("sub shipped confirm error: %v", err)
    }
    fmt.Println("sub shipped confirm response is:", response)
    return nil
}
```

## 通知处理 (notification)

### notificationConstant.go

```go
package notification

type NotificationType string
type SubNotificationType string

const (
    DID_NEW_TRANSACTION       NotificationType = "DID_NEW_TRANSACTION"
    DID_CHANGE_RENEWAL_STATUS NotificationType = "DID_CHANGE_RENEWAL_STATUS"
    REVOKE                    NotificationType = "REVOKE"
    RENEWAL_TIME_MODIFIED     NotificationType = "RENEWAL_TIME_MODIFIED"
    EXPIRE                    NotificationType = "EXPIRE"
)

const (
    INITIAL_BUY                              SubNotificationType = "INITIAL_BUY"
    DID_RENEW                                SubNotificationType = "DID_RENEW"
    RESTORE                                  SubNotificationType = "RESTORE"
    AUTO_RENEW_ENABLED                       SubNotificationType = "AUTO_RENEW_ENABLED"
    AUTO_RENEW_DISABLED                      SubNotificationType = "AUTO_RENEW_DISABLED"
    DOWNGRADE                                SubNotificationType = "DOWNGRADE"
    UPGRADE                                  SubNotificationType = "UPGRADE"
    REFUND_TRANSACTION                       SubNotificationType = "REFUND_TRANSACTION"
    BILLING_RETRY                            SubNotificationType = "BILLING_RETRY"
    PRICE_INCREASE                           SubNotificationType = "PRICE_INCREASE"
    BILLING_RECOVERY                         SubNotificationType = "BILLING_RECOVERY"
    PRODUCT_NOT_FOR_SALE                     SubNotificationType = "PRODUCT_NOT_FOR_SALE"
    APPLICATION_DELETE_SUBSCRIPTION_HOSTING   SubNotificationType = "APPLICATION_DELETE_SUBSCRIPTION_HOSTING"
    RENEWAL_EXTENDED                         SubNotificationType = "RENEWAL_EXTENDED"
)
```

### notificationPayload.go

```go
package notification

type NotificationPayload struct {
    NotificationType    string                `json:"notificationType"`
    NotificationSubtype string                `json:"notificationSubtype"`
    NotificationRequestId string             `json:"notificationRequestId"`
    NotificationMetaData *NotificationMetaData `json:"notificationMetaData"`
    NotificationVersion string                `json:"notificationVersion"`
    SignedTime          int64                 `json:"signedTime"`
}
```

### notificationMetaData.go

```go
package notification

type NotificationMetaData struct {
    Environment           string `json:"environment"`
    ApplicationId         string `json:"applicationId"`
    PackageName           string `json:"packageName"`
    Type                  int    `json:"type"`
    CurrentProductId      string `json:"currentProductId"`
    SubGroupId            string `json:"subGroupId"`
    SubGroupGenerationId  string `json:"subGroupGenerationId"`
    SubscriptionId        string `json:"subscriptionId"`
    PurchaseToken         string `json:"purchaseToken"`
    PurchaseOrderId       string `json:"purchaseOrderId"`
}
```

### appserver.go

```go
package notification

import (
    "encoding/json"
    "fmt"
)

/*
服务端关键事件通知处理

*/

type AppServer struct{}

// HandleNotificationRequest 处理接收到的通知请求（完整端到端流程）
// jwsNotification 是从HTTP请求body中获取的JWS字符串
// 需要传入JWSChecker实例进行验签
func (s *AppServer) HandleNotificationRequest(jwsNotification string, checkAndDecodeJWS func(string) (string, error)) error {
    // 步骤1: JWS验签
    notificationPayloadStr, err := checkAndDecodeJWS(jwsNotification)
    if err != nil {
        return fmt.Errorf("JWS verification failed: %v", err)
    }

    // 步骤2: 解析通知载荷
    var payload NotificationPayload
    if err := json.Unmarshal([]byte(notificationPayloadStr), &payload); err != nil {
        return fmt.Errorf("unmarshal error: %v", err)
    }

    metaData := payload.NotificationMetaData

    // 步骤3: 根据通知类型分发处理
    notificationType := NotificationType(payload.NotificationType)
    subtype := SubNotificationType(payload.NotificationSubtype)

    switch notificationType {
    case DID_NEW_TRANSACTION:
        // 新交易
        fmt.Printf("New transaction - subtype: %s, productId: %s, orderId: %s\n",
            subtype, metaData.CurrentProductId, metaData.PurchaseOrderId)
        // TODO: 查询订单 → 验证有效性 → 发放权益 → 确认发货
    case DID_CHANGE_RENEWAL_STATUS:
        // 续费状态变更
        fmt.Printf("Renewal status changed - subtype: %s, subscriptionId: %s\n",
            subtype, metaData.SubscriptionId)
        // TODO: 更新本地订阅状态记录
    case REVOKE:
        // 撤销
        fmt.Printf("Purchase revoked - subtype: %s, orderId: %s\n",
            subtype, metaData.PurchaseOrderId)
        // TODO: 撤回用户权益
    case RENEWAL_TIME_MODIFIED:
        // 续期时间变更
        fmt.Printf("Renewal time modified - subtype: %s\n", subtype)
        // TODO: 更新订阅到期时间
    case EXPIRE:
        // 过期
        fmt.Printf("Subscription expired - subtype: %s\n", subtype)
        // TODO: 标记订阅过期
    default:
        fmt.Println("Unknown notification type:", payload.NotificationType)
    }

    // 步骤4: 按subtype细分处理
    s.dealBySubtype(subtype, metaData)
    return nil
}

// DealNotificationV3 按subtype维度处理（向后兼容）
func (s *AppServer) DealNotificationV3(notificationPayloadStr string) error {
    var payload NotificationPayload
    if err := json.Unmarshal([]byte(notificationPayloadStr), &payload); err != nil {
        return fmt.Errorf("unmarshal error: %v", err)
    }

    subtype := SubNotificationType(payload.NotificationSubtype)
    s.dealBySubtype(subtype, payload.NotificationMetaData)
    return nil
}

func (s *AppServer) dealBySubtype(subtype SubNotificationType, metaData *NotificationMetaData) {
    switch subtype {
    case INITIAL_BUY:       // 首次购买
    case DID_RENEW:          // 续期成功
    case RESTORE:            // 恢复购买
    case AUTO_RENEW_ENABLED: // 开启自动续费
    case AUTO_RENEW_DISABLED:// 关闭自动续费
    case DOWNGRADE:          // 降级订阅
    case UPGRADE:            // 升级订阅
    case REFUND_TRANSACTION: // 退款
    case BILLING_RETRY:      // 扣费重试
    case PRICE_INCREASE:     // 价格上涨
    case BILLING_RECOVERY:   // 扣费恢复
    case PRODUCT_NOT_FOR_SALE: // 商品下架
    case APPLICATION_DELETE_SUBSCRIPTION_HOSTING: // 删除订阅托管
    case RENEWAL_EXTENDED:   // 续期延长
    default:
        fmt.Println("unknown notification subtype:", string(subtype))
    }
}
```

## 使用示例 (main.go)

```go
package main

import (
    "encoding/json"
    "fmt"
    "io/ioutil"
    "net/http"
    demo "iap-golang-sample/src/main/go/com/huawei/iap/server/demo"
    "iap-golang-sample/src/main/go/com/huawei/iap/server/demo/notification"
)

func main() {
    // 1. 生成JWT
    body := map[string]string{
        "purchaseOrderId": "purchaseOrderId",
        "purchaseToken":   "purchaseToken",
    }
    bodyJson, _ := json.Marshal(body)
    jwtGenerator := &demo.JWTGenerator{}
    jwtStr, err := jwtGenerator.GenJWT(string(bodyJson))
    if err != nil {
        fmt.Println("gen jwt error:", err)
    }
    fmt.Println("JWT:", jwtStr)

    // 2. 订单服务
    orderService := &demo.OrderService{}
    orderService.OrderStatusQuery("purchaseOrderId", "purchaseToken")
    orderService.OrderShippedConfirm("purchaseOrderId", "purchaseToken")

    // 3. 订阅服务
    subService := &demo.SubscriptionService{}
    subService.SubStatusQuery("purchaseOrderId", "purchaseToken")
    subService.SubShippedConfirm("purchaseOrderId", "purchaseToken")

    // 4. JWS验签
    jwsChecker := &demo.JWSChecker{}
    payload, err := jwsChecker.CheckAndDecodeJWS("jwsStr")
    if err != nil {
        fmt.Println("jws check error:", err)
    }
    fmt.Println("JWS payload:", payload)
}

```
