---
name: harmonyos-iap-integration
description: 基于HarmonyOS IAP Kit提供华为应用内支付接入执行指南，包括客户端和服务端完整实现、四种商品类型购买、支付结果处理、防掉单机制、JWS验签、关键事件通知等场景。当用户提到"HarmonyOS IAP"、"应用内购买"、"应用内支付"、"IAP Kit接入"或需要在HarmonyOS应用中实现付费功能时，使用此技能。
version: 1.0.0
---

# HarmonyOS IAP Kit 应用内支付接入技能

## 技能目标

为开发者提供一套可直接执行的HarmonyOS IAP Kit应用内支付接入流程，帮助快速完成从联调到上线的闭环。

覆盖能力：
- 消耗型商品购买： 例如游戏币、钻石、体力等
- 非消耗型商品购买：例如去广告、额外关卡、永久解锁等
- 自动续期订阅商品购买：例如月度VIP、年度会员、连续包月、连续包季、连续包年等
- 非续期订阅商品购买：例如季卡、月卡、年卡、一个月会员等

## 何时使用

当用户要做以下任一任务时启用本skill：
- 设计或实现华为鸿蒙应用内支付（HarmonyOS IAP Kit）
- 需要在鸿蒙应用中实现付费功能
- 排查掉单、重复发货、补单、订阅状态异常
- 制定沙盒联调和生产上线清单

## 交互策略

当用户请求接入IAP Kit时：
1. **完整了解项目情况**: 识别商品等IAP接入相关的关键信息
2. **先从当前项目识别需要的商品类型，无法识别时询问商品类型（多选）**：消耗型/非消耗型/自动续期订阅/非自动续期订阅
3. **先从当前项目识别需要的服务端语言，无法识别服务端语言时询问服务端语言**：Java/Python/Node.js/PHP/Golang
4. **【强制】严格按照标准流程执行，必须逐项检查每个环节是否已实现**：数字商品购买标准流程7个环节、数字商品补单标准流程4个环节、关键事件通知标准流程5个通知类型
5. **按需提供代码**：从references目录中读取对应的数据结构和demo代码，代码可以复用，TODO需要继续实现
6. **【强制】代码规范检查**：使用强类型DTO而非Map、JsonNode等弱类型（防止字段名拼写错误）
7. **【强制】实现完成后，必须使用标准流程检查每个环节都已实现**
---

## 生成代码或方案时的输出要求

- 客户端优先贴近官方ArkTS demo（references目录中）的结构和API命名
- 服务端优先贴近用户指定语言的官方demo（references目录中）
- 【重要】客户端和服务端使用的数据结构优先从`references/client-arkts.md`中查找

## 前置准备

1. 开启和激活应用内购买服务
2. 配置`module.json5`添加`client_id`
3. 在商品管理系统中配置商品并上架（状态必须为在线）
4. 配置手动签名（不能用自动签名），指导文档：https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/ide-signing#section297715173233

## 标准流程（必须严格遵守）

### 数字商品购买标准流程（必须完整实现所有环节）

```
环境检测（queryEnvironmentStatus） → 查询商品信息（queryProducts） → 服务器预下单 → 发起购买，拉起收银台（createPurchase） -> 上报PurchaseData → 服务器验签发放权益 → 端侧确认发货（finishPurchase）                                                                                                 
```

### 数字商品补单标准流程（必须完整实现所有环节）

```
查询已购但未确认发货的订单/订阅列表（queryPurchases） -> 上报PurchaseData → 服务器验签发放权益 → 端侧确认发货（finishPurchase）                                                                                                 
```

### 关键事件通知标准流程（必须完整实现所有环节）

```
IAP服务器发送订单/订阅关键事件通知 -> 根据不同的通知类型处理相关的业务逻辑 -> 根据业务处理结果返回响应                                                                                      
```
通知请求体样例：`{"jwsNotification":"xxx"}`

### 核心数据结构说明
1. 消耗型/非消耗型/非续期订阅商品走订单流程，状态数据结构为PurchaseOrderPayload，具体结构参考`references/client-arkts.md`
2. 自动续期订阅商品走订阅流程，状态数据结构为SubGroupStatusPayload，具体结构参考`references/client-arkts.md`

## 最佳实践

### 服务器预下单处理建议
1. 初始化业务订单并落库，业务订单建议包含应用账号、商品ID、价格、币种等字段
2. 返回订单号

### 发起购买，拉起收银台（createPurchase）处理建议
developerPayload使用预下单中返回的值

### 上报PurchaseData处理建议
1. 直接传递createPurchase/queryPurchases返回的purchaseData JSON字符串
2. purchaseData数据结构参考`references/client-arkts.md` 中的PurchaseData结构

### 服务器验签发放权益处理建议
1. 区分商品类型处理，对相应的jws状态数据进行验签并解码出对应的状态数据
2. 已经退款的订单不需要发放权益，条件为purchaseOrderRevocationReasonCode不为空
3. 已经发放过权益的订单不需要处理，防止重复发放权益
4. 根据developerPayload查询业务订单，根据订单信息中的用户账号和商品ID发放权益

### 数字商品补单标准流程
1. **应用启动时**：始终查询未完成的购买。
2. **购买返回`PRODUCT_OWNED`或`SYSTEM_ERROR`时**：表示存在未完成的购买

### 关键事件通知处理建议

1. DID_NEW_TRANSACTION通知处理建议：
```
区分商品类型查询最新状态信息 -> 验签并解码对应的jws数据 -> 判断是否需要发放权益 -> 根据developerPayload查询业务订单 -> 根据用户账号和商品ID发放权益 -> 区分商品类型调用IAP服务器确认发货接口
```

2. REVOKE通知处理建议：
```
区分商品类型查询最新状态信息 -> 验签并解码对应的jws数据 -> 判断是否需要回收权益 -> 根据developerPayload查询业务订单 -> 根据用户账号和商品ID回收业务订单对应的权益
```

#### 判断是否需要发放权益的处理建议
1. 已经退款的订单不需要发放权益，条件为purchaseOrderRevocationReasonCode不为空
2. 已经发放过权益的订单不需要处理，防止重复发放权益

#### 回收权益的处理建议
1. 回收业务订单中对应商品的权益，而非全部权益
2. 例如商品权益对应1个月会员，则回收一个月会员

## 常见错误码

| 错误码 | 含义 | 解决方案                                                                                            |
|--------|------|-------------------------------------------------------------------------------------------------|
| `1001860001` | 系统内部错误 | 若购买请求返回该错误码，建议通过queryPurchases接口确认用户是否存在已购但未发放权益的商品，及时发放权益                          |
| `1001860002` | 应用未被授权访问接口 | 1、检查应用程序签名/身份信息配置 2、检查应用的支付服务开关是否打开。                                                            |
| `1001860003` | 无效的商品信息 | 请登录AppGallery Connect网站，选择“我的应用 > 运营 > 商品管理 > 商品列表”，查看对应商品是否存在、必填信息是否完整、商品信息已经提交审核并审核通过。如未审核通过，可使用沙盒账号来测试 |
| `1001860004` | 接口访问过频 | 请控制接口调用频度。接口当前访问间隔时间默认为5s，后续IAP Kit可能会根据需要降低或提高速率限制                                             |
| `1001860005` | 网络连接异常 | 应用向用户给出提示，请用户检查网络                                                                               |
| `1001860007` | 商品所属的应用未在指定国家/地区上架 | 请登录AppGallery Connect网站，选择“我的应用 > 分发 > 版本信息 > 准备提交”，查看商品配置的国家/地区。                               |
| `1001860050` | 未登录华为账号 | 引导用户登录华为账号                                                                                      |
| `1001860051` | 商品已拥有 | 查询未完成购买并完成发货确认                                                                                  |
| `1001860052` | 由于未拥有该商品，发货失败 | 可通过queryPurchases接口确认用户是否购买了该商品                                                                                      |
| `1001860053` | 此次购买已经完成发货，无需重复发货 | 可通过queryPurchases接口查询是否有该商品的确认发货记录                                                                                      |
| `1001860054` | 用户账号所在服务地不在IAP Kit支持结算的国家/地区中 | 用户账号服务地为非中国境内（香港特别行政区、澳门特别行政区、中国台湾除外）地区。建议应用隐藏相关IAP功能入口               |
| `1001860056` | 用户交易被拒绝 | 建议稍后重试或更换支付方式                                                                                      |
| `1001860057` | 当前应用不是debug签名的应用 | 构建debug签名的应用                                                                                      |
| `1001860058` | 登录的华为账号不是配置的测试账号 | 需要在AppGallery Connect中的“用户与访问”中添加测试账号                                                                                     |

## 输出模板

所有回复使用以下结构：

```markdown
## 结论
[一句话说明当前是否可接入/可发布/已定位问题]

## 执行清单
- 已完成：
  - [item]
- 待完成：
  - [item]

## 风险与阻塞
- [风险或阻塞项]

## 下一步
1. [最小可执行动作]
2. [下一步验证动作]
```

## 参考资源（不要参考官方文档以外的文档）

- 开发指南：https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/iap-kit-guide
- API参考-端：https://developer.huawei.com/consumer/cn/doc/harmonyos-references/iap-arkts
- API参考-云：https://developer.huawei.com/consumer/cn/doc/harmonyos-references/iap-rest
- 官方demo-端：https://gitcode.com/HarmonyOS_Samples/iapkit-sample-clientdemo-arkts
- 官方demo-云：https://gitcode.com/HarmonyOS_Samples/iapkit-sample-serverdemo
