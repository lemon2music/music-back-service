# 服务端 Python 完整代码参考

所有代码来自华为官方demo: https://gitcode.com/HarmonyOS_Samples/iapkit-sample-serverdemo/tree/Python

## 项目结构

```
src/main/python/com/huawei/iap/server/demo/
  APIExample.py            # 入口示例
  IAPServer.py             # HTTP客户端基类
  JWTGenerator.py          # JWT生成器
  JWSChecker.py            # JWS验签
  OrderService.py          # 订单服务
  SubscriptionService.py   # 订阅服务
  notification/
    AppServer.py           # 通知处理
```

## 依赖

```
PyJWT
pyOpenSSL
cryptography
requests
```

## IAPServer.py - HTTP客户端基类

```python
import json
import urllib.request
import urllib.parse
import urllib.error
import JWTGenerator

class IAPServer:
    URL_ROOT = "https://iap.cloud.huawei.com"
    TIMEOUT_SECOND = 5
    WHITE_SPACE = " "

    def http_post(self, url, body_dict):
        json_data = json.dumps(body_dict)
        jwt = JWTGenerator.gen_jwt(json_data)
        headers = self.build_auth_headers(jwt)
        response, status_code = self.__do_post(url, str.encode(json_data), headers)
        if status_code == 401:
            print("request error: " + response)
        elif status_code != 200:
            print("request error: " + response)
        return response

    @staticmethod
    def __do_post(url, data, headers):
        try:
            error_pro = BetterHTTPErrorProcessor()
            opener = urllib.request.build_opener(error_pro)
            req = urllib.request.Request(url=url, data=data, headers=headers, unverifiable=False, method="POST")
            response = opener.open(req, timeout=IAPServer.TIMEOUT_SECOND)
            status_code = response.getcode()
            return str(response.read(), encoding="utf-8"), status_code
        except urllib.error.URLError as e:
            print(e)

    @staticmethod
    def build_auth_headers(jwt):
        auth_head = f"Bearer{IAPServer.WHITE_SPACE}{jwt}"
        return {
            "Content-Type": "application/json; charset=UTF-8",
            "Authorization": auth_head
        }

class BetterHTTPErrorProcessor(urllib.request.HTTPErrorProcessor):
    handler_order = 1000
    def http_response(self, request, response):
        code, msg, hdrs = response.code, response.msg, response.info()
        if not (200 <= code < 500):
            response = self.parent.error("http", request, response, code, msg, hdrs)
        return response
    https_response = http_response
```

## JWTGenerator.py - JWT生成器

```python
import datetime
import hashlib
import time
import jwt

class JWTGenerator:
    # TODO: 替换为实际的私钥文件路径
    JWT_PRI_KEY_PATH = "/path/to/key/priKey.p8"

    with open(JWT_PRI_KEY_PATH, "r", encoding="utf-8") as file:
        private_key = file.read()

    ACTIVE_TIME_SECOND = 3600

    JWT_HEADER = {
        "alg": "ES256",
        "typ": "JWT",
        "kid": "Key ID"       # TODO: 替换为实际的密钥ID
    }

    JWT_PAYLOAD = {
        "iss": "Issuer ID",   # TODO: 替换为实际的密钥发行者ID
        "aud": "iap-v1",
        "iat": 0,
        "exp": 0,
        "aid": "APP ID",      # TODO: 替换为实际的应用ID
        "digest": "",
    }

def gen_jwt(body_str: str) -> str:
    jwt_payload = JWTGenerator.JWT_PAYLOAD.copy()
    digest = create_digest(body_str)
    sign_time = int(datetime.datetime.fromtimestamp(
        time.time(), tz=datetime.timezone.utc).timestamp())
    jwt_payload.update({
        "iat": sign_time,
        "exp": sign_time + JWTGenerator.ACTIVE_TIME_SECOND,
        "digest": digest
    })
    iap_jwt = jwt.encode(jwt_payload, JWTGenerator.private_key,
                         JWTGenerator.JWT_HEADER.get("alg"),
                         headers=JWTGenerator.JWT_HEADER)
    return iap_jwt

def create_digest(body_str: str) -> str:
    hash_object = hashlib.sha256()
    hash_object.update(body_str.encode("utf-8"))
    return hash_object.hexdigest()
```

## JWSChecker.py - JWS验签

```python
import datetime
import time
from base64 import b64decode
from typing import List
import jwt
import requests
from OpenSSL import crypto
from OpenSSL.crypto import X509
from cryptography import x509
from cryptography.hazmat._oid import ExtensionOID
from cryptography.hazmat.backends import default_backend
from cryptography.hazmat.primitives import serialization

class JWSChecker:
    HEADER_PARAM_X5C = "x5c"
    HEADER_PARAM_ALG = "ES256"
    X5C_CHAIN_LENGTH = 3

    def __init__(self, trust_certificates: List[str]):
        self._cert_chain_checker = _InnerCertChainChecker(trust_certificates)

    def check_and_decode_jws(self, jws_str: str) -> dict:
        if jws_str is None or len(jws_str) == 0:
            raise Exception("jws_str must not empty")
        jws_headers: dict = jwt.get_unverified_header(jws_str)
        x5c_chain: List[str] = jws_headers.get(self.HEADER_PARAM_X5C)
        if x5c_chain is None or len(x5c_chain) != self.X5C_CHAIN_LENGTH:
            raise Exception("invalid x5c_chain length")
        alg_header: str = jws_headers.get("alg")
        if alg_header is None or self.HEADER_PARAM_ALG != alg_header:
            raise Exception("alg must be ES256")
        cert_pub_key = self._cert_chain_checker.verify_chain(x5c_chain)
        return jwt.decode(jws_str, cert_pub_key, algorithms=[self.HEADER_PARAM_ALG])

class _InnerCertChainChecker:
    LEAF_CERT_OID = "1.3.6.1.4.1.2011.2.415.1.1"
    CRL_TIME_OUT = 5
    CRL_SOFT_FAIL_ENABLED = False

    def __init__(self, trust_certificates: List[str], x509_strict_checks=True):
        self.x509_strict_checks = x509_strict_checks
        self.trust_certificates = trust_certificates

    def verify_chain(self, certificates: List[str]) -> str:
        x509_store = crypto.X509Store()
        x509_store.set_time(datetime.datetime.fromtimestamp(time.time(), tz=datetime.timezone.utc))
        for trusted_cert_str in self.trust_certificates:
            trusted_cert = crypto.load_certificate(crypto.FILETYPE_ASN1, b64decode(trusted_cert_str))
            x509_store.add_cert(trusted_cert)
        if self.x509_strict_checks:
            x509_store.set_flags(crypto.X509StoreFlags.X509_STRICT)
        iap_cert = crypto.load_certificate(crypto.FILETYPE_ASN1, b64decode(certificates[0], validate=True))
        mid_cert = crypto.load_certificate(crypto.FILETYPE_ASN1, b64decode(certificates[1], validate=True))
        x509_context = crypto.X509StoreContext(x509_store, iap_cert, [mid_cert])
        x509_context.verify_certificate()

        iap_cert.to_cryptography().extensions.get_extension_for_oid(
            x509.ObjectIdentifier(self.LEAF_CERT_OID))

        self.check_crl([iap_cert, mid_cert])

        return (iap_cert.to_cryptography().public_key()
                .public_bytes(encoding=serialization.Encoding.PEM,
                              format=serialization.PublicFormat.SubjectPublicKeyInfo)
                .decode())

    def check_crl(self, certificates: List[X509]):
        for cert in certificates:
            crl_url = cert.to_cryptography().extensions.get_extension_for_oid(
                ExtensionOID.CRL_DISTRIBUTION_POINTS).value[0].full_name[0].value
            try:
                response = requests.get(crl_url, timeout=self.CRL_TIME_OUT)
            except requests.exceptions.Timeout as e:
                if self.CRL_SOFT_FAIL_ENABLED:
                    continue
                raise
            if response is None:
                raise ValueError(f"the crl response is none, the crl_url is {crl_url}")
            crl = x509.load_der_x509_crl(response.content, default_backend())
            if crl.get_revoked_certificate_by_serial_number(cert.get_serial_number()):
                raise ValueError(f"the certificate is revoked, the crl_url is {crl_url}")

def fetch_pem_content(pem: str):
    return pem.replace("-----BEGIN CERTIFICATE-----", "").replace(
        "-----END CERTIFICATE-----", "").replace("\r\n", "").replace("\n", "")

def jws_check(jws: str):
    # TODO: 替换为实际的华为根CA证书路径
    root_cert = "/path/to/cer/RootCaG2Ecdsa.cer"
    with open(root_cert, "rb") as file:
        root_cert_data = file.read()
    root_cert_content = fetch_pem_content(root_cert_data.decode("utf-8"))
    jws_checker = JWSChecker([root_cert_content])
    return jws_checker.check_and_decode_jws(jws)
```

## OrderService.py - 订单服务

```python
from IAPServer import IAPServer

class OrderService(IAPServer):
    URL_ORDER_STATUS_QUERY = "/order/harmony/v1/application/order/status/query"
    URL_ORDER_SHIPPED_CONFIRM = "/order/harmony/v1/application/purchase/shipped/confirm"

    def order_status_query(self, purchase_order_id, purchase_token):
        """查询消耗型或非消耗型订单的最新状态"""
        body_dict = {"purchaseToken": purchase_token, "purchaseOrderId": purchase_order_id}
        response = self.http_post(self.URL_ROOT + self.URL_ORDER_STATUS_QUERY, body_dict)
        print(f"order status query response is {response}")

    def order_shipped_confirm(self, purchase_order_id, purchase_token):
        """确认消耗型或非消耗型商品已发货"""
        body_dict = {"purchaseToken": purchase_token, "purchaseOrderId": purchase_order_id}
        response = self.http_post(self.URL_ROOT + self.URL_ORDER_SHIPPED_CONFIRM, body_dict)
        print(f"order shipped confirm response is {response}")
```

## SubscriptionService.py - 订阅服务

```python
from IAPServer import IAPServer

class SubscriptionService(IAPServer):
    URL_SUB_STATUS_QUERY = "/subscription/harmony/v1/application/subscription/status/query"
    URL_SUB_SHIPPED_CONFIRM = "/subscription/harmony/v1/application/purchase/shipped/confirm"

    def sub_status_query(self, purchase_order_id, purchase_token):
        """查询自动续期订阅的最新状态"""
        body_dict = {"purchaseToken": purchase_token, "purchaseOrderId": purchase_order_id}
        response = self.http_post(self.URL_ROOT + self.URL_SUB_STATUS_QUERY, body_dict)
        print(f"sub status query response is {response}")

    def sub_shipped_confirm(self, purchase_order_id, purchase_token):
        """确认订阅商品已发货"""
        body_dict = {"purchaseToken": purchase_token, "purchaseOrderId": purchase_order_id}
        response = self.http_post(self.URL_ROOT + self.URL_SUB_SHIPPED_CONFIRM, body_dict)
        print(f"sub shipped confirm response is {response}")
```

## 通知处理 (notification/AppServer.py)

```python
import json
from dataclasses import dataclass
from typing import Optional
from JWSChecker import jws_check


# ============ 通知类型常量 ============

class NotificationType:
    """通知主类型"""
    DID_NEW_TRANSACTION = "DID_NEW_TRANSACTION"
    DID_CHANGE_RENEWAL_STATUS = "DID_CHANGE_RENEWAL_STATUS"
    REVOKE = "REVOKE"
    RENEWAL_TIME_MODIFIED = "RENEWAL_TIME_MODIFIED"
    EXPIRE = "EXPIRE"


class SubNotificationType:
    """通知子类型"""
    INITIAL_BUY = "INITIAL_BUY"
    DID_RENEW = "DID_RENEW"
    RESTORE = "RESTORE"
    AUTO_RENEW_ENABLED = "AUTO_RENEW_ENABLED"
    AUTO_RENEW_DISABLED = "AUTO_RENEW_DISABLED"
    DOWNGRADE = "DOWNGRADE"
    UPGRADE = "UPGRADE"
    REFUND_TRANSACTION = "REFUND_TRANSACTION"
    BILLING_RETRY = "BILLING_RETRY"
    PRICE_INCREASE = "PRICE_INCREASE"
    BILLING_RECOVERY = "BILLING_RECOVERY"
    PRODUCT_NOT_FOR_SALE = "PRODUCT_NOT_FOR_SALE"
    APPLICATION_DELETE_SUBSCRIPTION_HOSTING = "APPLICATION_DELETE_SUBSCRIPTION_HOSTING"
    RENEWAL_EXTENDED = "RENEWAL_EXTENDED"


# ============ 通知数据模型（强类型） ============

@dataclass
class NotificationMetaData:
    """通知元数据"""
    environment: str = ""
    applicationId: str = ""
    packageName: str = ""
    type: int = 0  # 商品类型: 0-消耗型, 1-非消耗型, 2-自动续期订阅, 3-非自动续期订阅
    currentProductId: str = ""
    subGroupId: str = ""
    subGroupGenerationId: str = ""
    subscriptionId: str = ""
    purchaseToken: str = ""
    purchaseOrderId: str = ""

    @classmethod
    def from_dict(cls, data: dict) -> "NotificationMetaData":
        return cls(**{k: v for k, v in data.items() if k in cls.__dataclass_fields__})


@dataclass
class NotificationPayload:
    """通知载荷"""
    notificationType: str = ""
    notificationSubtype: str = ""
    notificationRequestId: str = ""
    notificationMetaData: Optional[NotificationMetaData] = None
    notificationVersion: str = ""
    signedTime: int = 0

    @classmethod
    def from_dict(cls, data: dict) -> "NotificationPayload":
        meta_data = data.pop("notificationMetaData", None)
        payload = cls(**{k: v for k, v in data.items() if k in cls.__dataclass_fields__})
        if meta_data:
            payload.notificationMetaData = NotificationMetaData.from_dict(meta_data)
        return payload


# ============ 通知处理 ============

class AppServer:
    """
    服务端关键事件通知处理

    """

    @staticmethod
    def handle_notification_request(jws_notification: str):
        """
        处理接收到的通知请求（完整端到端流程）
        :param jws_notification: 请求body中的JWS字符串
        """
        # 步骤1: JWS验签
        notification_payload_dict = jws_check(jws_notification)

        # 步骤2: 解析为强类型
        payload = NotificationPayload.from_dict(notification_payload_dict)
        meta_data = payload.notificationMetaData

        # 步骤3: 根据通知类型分发处理
        notification_type = payload.notificationType
        notification_subtype = payload.notificationSubtype

        if notification_type == NotificationType.DID_NEW_TRANSACTION:
            # 新交易
            print(f"New transaction - subtype: {notification_subtype}, "
                  f"productId: {meta_data.currentProductId}, orderId: {meta_data.purchaseOrderId}")
            # TODO: 查询订单 → 验证有效性 → 发放权益 → 确认发货
        elif notification_type == NotificationType.DID_CHANGE_RENEWAL_STATUS:
            # 续费状态变更
            print(f"Renewal status changed - subtype: {notification_subtype}, "
                  f"subscriptionId: {meta_data.subscriptionId}")
            # TODO: 更新本地订阅状态记录
        elif notification_type == NotificationType.REVOKE:
            # 撤销
            print(f"Purchase revoked - subtype: {notification_subtype}, "
                  f"orderId: {meta_data.purchaseOrderId}")
            # TODO: 撤回用户权益
        elif notification_type == NotificationType.RENEWAL_TIME_MODIFIED:
            # 续期时间变更
            print(f"Renewal time modified - subtype: {notification_subtype}")
            # TODO: 更新订阅到期时间
        elif notification_type == NotificationType.EXPIRE:
            # 过期
            print(f"Subscription expired - subtype: {notification_subtype}")
            # TODO: 标记订阅过期
        else:
            print(f"Unknown notification type: {notification_type}")

        # 步骤4: 按subtype细分处理
        AppServer._deal_by_subtype(notification_subtype, meta_data)

    @staticmethod
    def deal_notification_v3(notification_payload_str: str):
        """按subtype维度处理（向后兼容）"""
        notification_payload: dict = json.loads(notification_payload_str)
        notification_subtype = notification_payload.get("notificationSubtype")
        meta_data_dict = notification_payload.get("notificationMetaData", {})
        meta_data = NotificationMetaData.from_dict(meta_data_dict)
        AppServer._deal_by_subtype(notification_subtype, meta_data)

    @staticmethod
    def _deal_by_subtype(notification_subtype: str, meta_data: NotificationMetaData):
        switcher = {
            SubNotificationType.INITIAL_BUY: lambda: print("首次购买"),
            SubNotificationType.DID_RENEW: lambda: print("续期成功"),
            SubNotificationType.RESTORE: lambda: print("恢复购买"),
            SubNotificationType.AUTO_RENEW_ENABLED: lambda: print("开启自动续费"),
            SubNotificationType.AUTO_RENEW_DISABLED: lambda: print("关闭自动续费"),
            SubNotificationType.DOWNGRADE: lambda: print("降级订阅"),
            SubNotificationType.UPGRADE: lambda: print("升级订阅"),
            SubNotificationType.REFUND_TRANSACTION: lambda: print("退款"),
            SubNotificationType.BILLING_RETRY: lambda: print("扣费重试"),
            SubNotificationType.PRICE_INCREASE: lambda: print("价格上涨"),
            SubNotificationType.BILLING_RECOVERY: lambda: print("扣费恢复"),
            SubNotificationType.PRODUCT_NOT_FOR_SALE: lambda: print("商品下架"),
            SubNotificationType.APPLICATION_DELETE_SUBSCRIPTION_HOSTING: lambda: print("应用删除订阅托管"),
            SubNotificationType.RENEWAL_EXTENDED: lambda: print("续期延长"),
        }
        switcher.get(notification_subtype, lambda: print("Unknown subtype"))()
```

## 使用示例 (APIExample.py)

```python
import json
from JWSChecker import jws_check
from JWTGenerator import gen_jwt
from OrderService import OrderService
from SubscriptionService import SubscriptionService
from notification.AppServer import AppServer

if __name__ == "__main__":
    # 1. 生成JWT
    body = {"purchaseToken": "testPurchaseToken", "purchaseOrderId": "testPurchaseOrderId"}
    encode_jwt = gen_jwt(json.dumps(body))

    # 2. 订单服务
    OrderService().order_status_query("purchaseOrderId", "purchaseToken")
    OrderService().order_shipped_confirm("purchaseOrderId", "purchaseToken")

    # 3. 订阅服务
    SubscriptionService().sub_status_query("purchaseOrderId", "purchaseToken")
    SubscriptionService().sub_shipped_confirm("purchaseOrderId", "purchaseToken")

    # 4. JWS验签
    jws_payload = jws_check("jwsStr")

    # 5. 处理通知（完整流程：JWS验签 + 业务分发）
    jws_notification = "eyJ..."  # 华为IAP服务器发送的JWS字符串
    AppServer.handle_notification_request(jws_notification)

    # 5b. 也可以分步处理：先验签再处理
    notification_payload_str = json.dumps(jws_check(jws_notification))
    AppServer.deal_notification_v3(notification_payload_str)
```
