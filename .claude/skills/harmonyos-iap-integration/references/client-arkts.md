# 客户端 ArkTS 完整代码参考

所有代码来自华为官方demo: https://gitcode.com/HarmonyOS_Samples/iapkit-sample-clientdemo-arkts

## 项目结构

```
entry/src/main/
  ets/
    common/
      IapDataModel.ets    # 数据模型定义
      JWSUtil.ets          # JWS解码工具
      Logger.ets           # 日志工具
    entryability/
      EntryAbility.ets     # 应用入口Ability
    pages/
      EntryPage.ets         # 入口页面（导航）
      ConsumablesPage.ets   # 消耗型商品页面
      NonConsumablesPage.ets # 非消耗型商品页面
      SubscriptionsPage.ets # 自动续期订阅页面
      NonRenewablesPage.ets # 非自动续期订阅页面
  module.json5
  resources/base/profile/
    main_pages.json
    route_map.json
```

## 数据模型 (IapDataModel.ets)

```typescript
/**
 * 购买结果数据
 * createPurchase / queryPurchases 返回的 purchaseData JSON 字符串解析后的结构
 */
export interface PurchaseData {
  /** 商品类型: 0-消耗型, 1-非消耗型, 2-自动续期订阅, 3-非自动续期订阅 */
  type: number;
  /** JWS编码的购买订单信息（消耗型/非消耗型/非自动续期订阅） */
  jwsPurchaseOrder?: string;
  /** JWS编码的订阅组状态信息（仅自动续期订阅） */
  jwsSubscriptionStatus?: string;
}

/**
 * 购买订单载荷 — jwsPurchaseOrder 解码验签后得到的完整数据
 * 官方文档: https://developer.huawei.com/consumer/cn/doc/harmonyos-references/iap-data-model
 */
export interface PurchaseOrderPayload {
  /** 应用ID (最大128字符) */
  applicationId: string;
  /** 商品ID (最大256字符) */
  productId: string;
  /** 商品类型: 0-消耗型, 1-非消耗型, 2-自动续期订阅, 3-非自动续期订阅 */
  productType: number;
  /** 购买订单ID，唯一标识一笔订单，一旦生成不会变化 (最大64字符) */
  purchaseOrderId: string;
  /** 购买令牌，自动续期订阅场景中与subscriptionId一一对应 (最大512字符) */
  purchaseToken: string;
  /** 购买时间戳(ms) */
  purchaseTime: number;
  /** JWS签名时间戳(ms) */
  signedTime: number;
  /** 用户国家/地区代码 */
  countryCode: string;
  /** 商品价格 */
  price: number;
  /** 币种代码(如CNY, USD) */
  currency: string;
  /** 环境标识: Sandbox-沙盒, Production-生产 */
  environment: string;
  /** 发货状态: 1-已发货, 2-未发货 */
  finishStatus: FinishStatus;
  /** 是否需要调用finishPurchase确认发货 */
  needFinish: boolean;
  /** 商户侧保留信息，由开发者在调用支付接口时传入，续费订单和服务端通知中100%原样返回 */
  developerPayload?: string;
  /** 购买订单撤销原因码，为空代表购买成功 */
  purchaseOrderRevocationReasonCode?: string;
  /** 优惠ID (最大256字符) */
  offerId?: string;
  /** 订阅ID (最大64字符)，订阅场景使用 */
  subscriptionId?: string;
  /** 订阅组生成ID (最大512字符)，订阅场景使用 */
  subGroupGenerationId?: string;
}

/** 发货状态枚举 */
export enum FinishStatus {
  /** 已发货 */
  FINISHED = '1',
  /** 未发货 */
  UNFINISHED = '2'
}

/**
 * 订阅组状态载荷 — jwsSubscriptionStatus 解码验签后得到的完整数据
 */
export interface SubGroupStatusPayload {
  /** 环境标识: Sandbox-沙盒, Production-生产 */
  environment: string;
  /** 应用ID */
  applicationId: string;
  /** 应用包名 */
  packageName: string;
  /** 订阅组ID */
  subGroupId: string;
  /** 最新订阅状态 */
  lastSubscriptionStatus?: SubscriptionStatus;
  /** 历史订阅状态列表 */
  historySubscriptionStatusList?: SubscriptionStatus[];
}

/**
 * 订阅状态
 */
export interface SubscriptionStatus {
  /** 订阅组生成ID */
  subGroupGenerationId: string;
  /** 订阅ID，与purchaseToken一一对应 */
  subscriptionId: string;
  /** 购买令牌，续费时不变，切换订阅时变化 */
  purchaseToken: string;
  /** 订阅状态: '1'-生效中, '2'-已过期, '3'-扣费重试中, '5'-已撤销 */
  status: SubStatus;
  /** 订阅到期时间戳(ms) */
  expiresTime: number;
  /** 最新购买订单 */
  lastPurchaseOrder?: PurchaseOrderPayload;
  /** 续期信息 */
  renewalInfo?: SubRenewalInfo;
}

/** 续期信息 */
export interface SubRenewalInfo {
  /** 续期商品ID */
  productId: string;
}

/** 订阅状态枚举 */
export enum SubStatus {
  /** 生效中 */
  ACTIVE = '1',
  /** 已过期 */
  EXPIRED = '2',
  /** 扣费重试中 */
  CHARGE_ATTEMPT = '3',
  /** 已撤销 */
  REVOKED = '5'
}

/**
 * 服务端REST API响应 — 订单状态查询
 * POST /order/harmony/v1/application/order/status/query
 */
export interface OrderStatusQueryResponse {
  /** 响应码，0表示成功 */
  responseCode: string;
  /** 响应描述 */
  responseMessage?: string;
  /** JWS编码的购买订单信息，解码后为PurchaseOrderPayload */
  jwsPurchaseOrder?: string;
}

/**
 * 服务端REST API响应 — 订阅状态查询
 * POST /subscription/harmony/v1/application/subscription/status/query
 */
export interface SubStatusQueryResponse {
  /** 响应码，0表示成功 */
  responseCode: string;
  /** 响应描述 */
  responseMessage?: string;
  /** JWS编码的订阅组状态信息，解码后为SubGroupStatusPayload */
  jwsSubGroupStatus?: string;
}

/**
 * 服务端关键事件通知载荷 — 通知JWS解码后得到的数据
 */
export interface NotificationPayload {
  /** 通知类型 */
  notificationType: string;
  /** 通知子类型 */
  notificationSubtype: string;
  /** 通知请求ID */
  notificationRequestId: string;
  /** 通知元数据 */
  notificationMetaData: NotificationMetaData;
  /** 通知版本 */
  notificationVersion: string;
  /** 签名时间戳(ms) */
  signedTime: number;
}

/**
 * 通知元数据
 */
export interface NotificationMetaData {
  /** 环境标识 */
  environment: string;
  /** 应用ID */
  applicationId: string;
  /** 应用包名 */
  packageName: string;
  /** 商品类型: 0-消耗型, 1-非消耗型, 2-自动续期订阅, 3-非自动续期订阅 */
  type: number;
  /** 当前商品ID */
  currentProductId: string;
  /** 订阅组ID */
  subGroupId?: string;
  /** 订阅组生成ID */
  subGroupGenerationId?: string;
  /** 订阅ID */
  subscriptionId?: string;
  /** 购买令牌 */
  purchaseToken: string;
  /** 购买订单ID */
  purchaseOrderId: string;
}
```

## JWS解码工具 (JWSUtil.ets)

```typescript
import { util } from '@kit.ArkTS';
import Logger from './Logger';

const TAG: string = 'JWSUtil';
const BASE64_PADDING_MOD: number = 4;
const BASE64_PADDING_INVALID: number = 1;

export class JWSUtil {
  public static decodeJwsObj(data: string): string {
    const jws: string[] = data.split('.');
    let result: string = '';
    if (jws.length < 3) {
      return result;
    }
    try {
      const textDecoder = util.TextDecoder.create('utf-8', { ignoreBOM: true });
      const base64 = new util.Base64Helper();
      let payload = jws[1];
      const centerLineRegex: RegExp = new RegExp('-', 'g');
      const underLineRegex: RegExp = new RegExp('_', 'g');
      payload = payload.replace(centerLineRegex, '+').replace(underLineRegex, '/');
      const pad = payload.length % BASE64_PADDING_MOD;
      if (pad) {
        if (pad === BASE64_PADDING_INVALID) {
          throw new Error('InvalidLengthError: Input base64 string is the wrong length to determine padding');
        }
        payload += new Array(BASE64_PADDING_MOD - pad + 1).join('=');
      }
      result = textDecoder.decodeToString(base64.decodeSync(payload));
    } catch (err) {
      Logger.error(TAG, `decodeJwsObj parse err: ${JSON.stringify(err)}`);
    }
    return result;
  }
}
```

## 日志工具 (Logger.ets)

```typescript
import { hilog } from '@kit.PerformanceAnalysisKit';

export default class Logger {
  private static IAP_DEMO_PREFIX: string = '[IAP_Demo]';
  private static IAP_LOG_DOMIN = 0x0fff;
  private static TWO_PLACEHOLDER_FORMAT: string = '%{public}s    %{public}s';

  public static info(tag: string, log: string) {
    hilog.info(Logger.IAP_LOG_DOMIN, Logger.IAP_DEMO_PREFIX, Logger.TWO_PLACEHOLDER_FORMAT, tag, log);
  }
  public static debug(tag: string, log: string) {
    hilog.debug(Logger.IAP_LOG_DOMIN, Logger.IAP_DEMO_PREFIX, Logger.TWO_PLACEHOLDER_FORMAT, tag, log);
  }
  public static warn(tag: string, log: string) {
    hilog.warn(Logger.IAP_LOG_DOMIN, Logger.IAP_DEMO_PREFIX, Logger.TWO_PLACEHOLDER_FORMAT, tag, log);
  }
  public static error(tag: string, log: string) {
    hilog.error(Logger.IAP_LOG_DOMIN, Logger.IAP_DEMO_PREFIX, Logger.TWO_PLACEHOLDER_FORMAT, tag, log);
  }
}
```

## 入口页面 (EntryPage.ets)

```typescript
import { router } from '@kit.ArkUI'

@Entry
@Component
struct EntryPage {
  private vpValue = this.getUIContext().px2vp(136);
  pageStack: NavPathStack = new NavPathStack();

  build() {
    Navigation(this.pageStack) {
      Column() {
        Column() {}.backgroundColor('#F1F3F5').width("100%").height(this.vpValue)
        Column() {
          CustomButton({ mText: 'Consumables', mName: 'ConsumablesPage', pageStack: this.pageStack });
          CustomButton({ mText: 'NonConsumables', mName: 'NonConsumablesPage', pageStack: this.pageStack });
          CustomButton({ mText: 'Subscriptions', mName: 'SubscriptionsPage', pageStack: this.pageStack });
          CustomButton({ mText: 'NonRenewables', mName: 'NonRenewablesPage', pageStack: this.pageStack });
        }.padding({ left: 24, right: 24 }).width('100%').height('100%').backgroundColor('#F1F3F5')
      }
    }.hideTitleBar(true)
  }
}

@Component
export struct CustomButton {
  private mText: string = '';
  private mName: string = '';
  private pageStack: NavPathStack = new NavPathStack();
  private mOnClick: ((event?: object) => void) | null = null

  build() {
    Button() {
      Text(this.mText).fontSize(18).fontWeight(FontWeight.Bold)
    }
    .id(this.mText).type(ButtonType.Capsule).margin({ top: 12 })
    .width('100%').height(40).backgroundColor('#0D9FFB')
    .onClick((event) => {
      if (this.mOnClick) { this.mOnClick(event); return; }
      this.pageStack.pushPath({ name: this.mName });
    })
  }
}
```

## 消耗型商品页面 (ConsumablesPage.ets)

```typescript
import { iap } from '@kit.IAPKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { common } from '@kit.AbilityKit';
import { promptAction } from '@kit.ArkUI';
import { JWSUtil } from '../common/JWSUtil';
import Logger from '../common/Logger';
import { FinishStatus, PurchaseData, PurchaseOrderPayload } from '../common/IapDataModel';

const TAG: string = 'ConsumablesPage';

@Builder
export function ConsumablesPageBuilder() {
  ConsumablesPage();
}

@Entry
@Component
struct ConsumablesPage {
  private context: common.UIAbilityContext = {} as common.UIAbilityContext;
  private vpValue = this.getUIContext().px2vp(136);
  @State querying: boolean = true;
  @State queryingFailed: Boolean = false;
  @State productInfoArray: iap.Product[] = [];
  @State queryFailedText: string = 'Query failed';

  aboutToAppear() {
    this.context = this.getUIContext().getHostContext() as common.UIAbilityContext;
    this.onCase();
  }

  async onCase() {
    this.showLoadingPage();
    const queryEnvCode = await this.queryEnv();
    if (queryEnvCode !== 0) {
      let queryEnvFailedText = 'This app does not support iap';
      if (queryEnvCode === iap.IAPErrorCode.ACCOUNT_NOT_LOGGED_IN) {
        queryEnvFailedText = 'Go to Settings and log in to your Huawei ID and try again.';
      }
      this.showFailedPage(queryEnvFailedText);
      return;
    }
    await this.queryPurchases();
    this.queryProducts();
  }

  async queryEnv(): Promise<number> {
    return new Promise<number>((resolve) => {
      iap.queryEnvironmentStatus(this.context).then(() => {
        Logger.info(TAG, 'Succeeded in querying environment status.');
        resolve(0);
      }).catch((err: BusinessError) => {
        Logger.error(TAG, `Failed to query environment status. Code is ${err.code}, message is ${err.message}`);
        resolve(err.code);
      })
    });
  }

  async queryPurchases(): Promise<void> {
    return new Promise<void>((resolve) => {
      const param: iap.QueryPurchasesParameter = {
        productType: iap.ProductType.CONSUMABLE,
        queryType: iap.PurchaseQueryType.UNFINISHED
      };
      iap.queryPurchases(this.context, param).then((res: iap.QueryPurchaseResult) => {
        Logger.info(TAG, 'Succeeded in querying purchases.');
        const purchaseDataList: string[] = res.purchaseDataList;
        if (purchaseDataList === undefined || purchaseDataList.length <= 0) {
          Logger.info(TAG, 'queryPurchases, purchaseDataList empty');
          resolve();
          return;
        }
        for (let i = 0; i < purchaseDataList.length; i++) {
          this.dealPurchaseData(purchaseDataList[i]);
        }
        resolve();
      }).catch((err: BusinessError) => {
        Logger.error(TAG, `Failed to query purchases. Code is ${err.code}, message is ${err.message}`);
        resolve();
      });
    });
  }

  dealPurchaseData(purchaseData: string) {
    try {
      // 将purchaseData发送到应用服务器进行签名验证
      const jwsPurchaseOrder = (JSON.parse(purchaseData) as PurchaseData).jwsPurchaseOrder;
      if (!jwsPurchaseOrder) {
        Logger.error(TAG, 'dealPurchaseData, jwsPurchaseOrder invalid');
        return;
      }
      // 解码jwsPurchaseOrder并进行签名验证
      const purchaseOrderStr = JWSUtil.decodeJwsObj(jwsPurchaseOrder);
      const purchaseOrderPayload = JSON.parse(purchaseOrderStr) as PurchaseOrderPayload;
      // TODO 按照实际场景发放权益
      
      // 发货成功后调用finishPurchase确认发货
      if (purchaseOrderPayload && purchaseOrderPayload.finishStatus !== FinishStatus.FINISHED) {
        this.finishPurchase(purchaseOrderPayload);
      }
    } catch (e) {
      Logger.error(TAG, 'dealPurchaseData json error');
    }
  }

  finishPurchase(purchaseOrder: PurchaseOrderPayload) {
    if (purchaseOrder.productType === undefined || purchaseOrder.productType === null) {
      Logger.error(TAG, 'finishPurchase but productType is empty');
      return;
    }
    const finishPurchaseParam: iap.FinishPurchaseParameter = {
      productType: purchaseOrder.productType,
      purchaseToken: purchaseOrder.purchaseToken,
      purchaseOrderId: purchaseOrder.purchaseOrderId
    };
    iap.finishPurchase(this.context, finishPurchaseParam).then(() => {
      Logger.info(TAG, 'Succeeded in finishing purchase.');
    }).catch((err: BusinessError) => {
      Logger.error(TAG, `Failed to finish purchase. Code is ${err.code}, message is ${err.message}`);
    });
  }

  queryProducts() {
    const queryProductParam: iap.QueryProductsParameter = {
      productType: iap.ProductType.CONSUMABLE,
      productIds: ['ohos_consume_001']  // 替换为实际商品ID
    };
    iap.queryProducts(this.context, queryProductParam).then((result) => {
      Logger.info(TAG, 'Succeeded in querying products.');
      this.productInfoArray = result;
      this.showNormalPage();
    }).catch((err: BusinessError) => {
      Logger.error(TAG, `Failed to query products. Code is ${err.code}, message is ${err.message}`);
      this.showFailedPage();
    });
  }

  buy(id: string, type: iap.ProductType) {
    try {
      // TODO 服务器预下单获取orderId
      const orderId = "预下单返回"
      const createPurchaseParam: iap.PurchaseParameter = {
        productId: id,
        productType: type,
        developerPayload: orderId
      }
      iap.createPurchase(this.context, createPurchaseParam).then((result) => {
        const msg: string = 'Succeeded in creating purchase.';
        Logger.info(TAG, msg);
        promptAction.openToast({ message: msg, duration: 2000 });
        this.dealPurchaseData(result.purchaseData);
      }).catch((err: BusinessError) => {
        const msg: string = `Failed to create purchase. Code is ${err.code}, message is ${err.message}`;
        Logger.error(TAG, msg);
        promptAction.openToast({ message: msg, duration: 2000 });
        if (err.code === iap.IAPErrorCode.PRODUCT_OWNED || err.code === iap.IAPErrorCode.SYSTEM_ERROR) {
          this.queryPurchases();
        }
      })
    } catch (err) {
      const e: BusinessError = err as BusinessError;
      Logger.error(TAG, `Failed to create purchase. Code is ${e.code}, message is ${e.message}`);
    }
  }

  showLoadingPage() { this.queryingFailed = false; this.querying = true; }
  showFailedPage(failedText?: string) {
    if (failedText) this.queryFailedText = failedText;
    this.queryingFailed = true; this.querying = false;
  }
  showNormalPage() { this.queryingFailed = false; this.querying = false; }

  build() {
    NavDestination() {
      Flex({ direction: FlexDirection.Column }) {
        Column() {}.backgroundColor('#F1F3F5').width("100%").height(this.vpValue)
        Column() {
          Row() {
            Text('Consumables').fontSize(28).fontWeight(FontWeight.Bold).margin({ left: 24, right: 24 })
          }.margin({ top: 16, bottom: 12 }).height(48).justifyContent(FlexAlign.Start).width('100%')
          List({ space: 0, initialIndex: 0 }) {
            ForEach(this.productInfoArray, (item: iap.Product) => {
              ListItem() {
                Flex({ direction: FlexDirection.Row, alignItems: ItemAlign.Center }) {
                  Image($r('app.media.fortune')).height(48).width(48).objectFit(ImageFit.Contain)
                  Text(item.name).width('100%').height(48).fontSize(16).textAlign(TextAlign.Start).padding({ left: 12, right: 12 })
                  Button(item.localPrice ? item.localPrice : 'Buy').width(200).fontSize(16).height(30)
                    .onClick(() => { 
                    // 
                    this.buy(item.id, item.type) 
                    }).stateEffect(true)
                }.borderRadius(16).backgroundColor('#FFFFFF').alignSelf(ItemAlign.Auto)
              }
            })
          }.divider({ strokeWidth: 1, startMargin: 2, endMargin: 2 })
          .padding({ left: 12, right: 12 }).margin({ left: 12, right: 12 })
          .borderRadius(16).backgroundColor('#FFFFFF').alignSelf(ItemAlign.Auto)
        }.backgroundColor('#F1F3F5').width('100%').height('100%')
        .visibility(this.querying || this.queryingFailed ? Visibility.None : Visibility.Visible)

        Stack() { LoadingProgress().width(96).height(96) }
        .backgroundColor('#F1F3F5').width('100%').height('100%')
        .visibility(this.querying ? Visibility.Visible : Visibility.None)

        Stack({ alignContent: Alignment.Center }) {
          Text(this.queryFailedText).fontSize(28).fontWeight(FontWeight.Bold).margin({ left: 24, right: 24 })
        }.backgroundColor('#F1F3F5').width('100%').height('100%')
        .visibility(this.queryingFailed ? Visibility.Visible : Visibility.None)
        .onClick(() => { this.onCase(); })
      }
    }.hideTitleBar(true)
  }
}
```

## 非消耗型商品页面 (NonConsumablesPage.ets)

与消耗型的主要差异：
- `productType` 使用 `iap.ProductType.NONCONSUMABLE`
- `queryPurchases` 使用 `iap.PurchaseQueryType.CURRENT_ENTITLEMENT`（查询当前权益）
- 已购买的商品标记为"已拥有"，按钮禁用
- 先查询商品，再查询已购买的权益

```typescript
import { iap } from '@kit.IAPKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { common } from '@kit.AbilityKit';
import { promptAction } from '@kit.ArkUI';
import { JWSUtil } from '../common/JWSUtil';
import Logger from '../common/Logger';
import { FinishStatus, PurchaseData, PurchaseOrderPayload } from '../common/IapDataModel';

const TAG: string = 'NonConsumablesPage';

@Builder
export function NonConsumablesPageBuilder() {
  NonConsumablesPage();
}

@Entry
@Component
struct NonConsumablesPage {
  private context: common.UIAbilityContext = {} as common.UIAbilityContext;
  @State productInfoArray: ProductInfo[] = [];
  // ... 其他状态变量同ConsumablesPage

  async onCase() {
    this.showLoadingPage();
    const queryEnvCode = await this.queryEnv();
    if (queryEnvCode !== 0) { /* 同消耗型 */ return; }
    const queryProductsCode = await this.queryProducts();
    queryProductsCode === 0 && this.queryPurchases();
  }

  // 关键差异: 使用CURRENT_ENTITLEMENT查询已拥有权益
  queryPurchases(): Promise<void> {
    return new Promise<void>((resolve) => {
      const param: iap.QueryPurchasesParameter = {
        productType: iap.ProductType.NONCONSUMABLE,
        queryType: iap.PurchaseQueryType.CURRENT_ENTITLEMENT
      };
      iap.queryPurchases(this.context, param).then((res: iap.QueryPurchaseResult) => {
        const purchaseDataList: string[] = res.purchaseDataList;
        if (!purchaseDataList || purchaseDataList.length <= 0) { resolve(); return; }
        for (let i = 0; i < purchaseDataList.length; i++) {
          this.dealPurchaseData(purchaseDataList[i]);
        }
        resolve();
      }).catch((err: BusinessError) => { resolve(); }).finally(() => { this.showNormalPage(); });
    });
  }

  // 处理购买数据时标记商品为已购买
  dealPurchaseData(purchaseData: string) {
    try {
      const jwsPurchaseOrder = (JSON.parse(purchaseData) as PurchaseData).jwsPurchaseOrder;
      if (!jwsPurchaseOrder) return;
      const purchaseOrderStr = JWSUtil.decodeJwsObj(jwsPurchaseOrder);
      const purchaseOrderPayload = JSON.parse(purchaseOrderStr) as PurchaseOrderPayload;
      if (!purchaseOrderPayload) return;
      // 标记商品为已拥有
      this.setProductInfoConsume(purchaseOrderPayload.productId, true);
      if (purchaseOrderPayload.finishStatus !== FinishStatus.FINISHED) {
        this.finishPurchase(purchaseOrderPayload);
      }
    } catch (e) { Logger.error(TAG, 'dealPurchaseData json error'); }
  }

  private setProductInfoConsume(productId: string, isConsume: boolean) {
    for (let i = 0; i < this.productInfoArray.length; i++) {
      if (this.productInfoArray[i].id === productId) {
        const curProduct: ProductInfo = JSON.parse(JSON.stringify(this.productInfoArray[i]));
        curProduct.isConsume = isConsume;
        this.productInfoArray[i] = curProduct;
        return;
      }
    }
  }

  // 购买前检查是否已拥有
  buy(id: string, type: iap.ProductType, isConsumables?: boolean) {
    if (isConsumables) {
      promptAction.openToast({ message: 'aready owned', duration: 2000 });
      return;
    }
    // ... 后续购买流程同消耗型
  }

  queryProducts(): Promise<number> {
    return new Promise<number>((resolve) => {
      const queryProductParam: iap.QueryProductsParameter = {
        productType: iap.ProductType.NONCONSUMABLE,
        productIds: ['NC00001']  // 替换为实际商品ID
      };
      iap.queryProducts(this.context, queryProductParam).then((result) => {
        this.productInfoArray = result;
        resolve(0);
      }).catch((err: BusinessError) => {
        this.showFailedPage();
        resolve(err.code);
      });
    });
  }

  // UI中按钮显示"owned"或价格
  // Button(item?.isConsume ? 'owned' : (item.localPrice ? item.localPrice : 'Buy'))
  //   .enabled(!item?.isConsume)
}

interface ProductInfo extends iap.Product {
  isConsume?: boolean;
}
```

## 自动续期订阅页面 (SubscriptionsPage.ets)

与消耗型的主要差异：
- `productType` 使用 `iap.ProductType.AUTORENEWABLE`
- 购买结果使用 `jwsSubscriptionStatus`（非 `jwsPurchaseOrder`）
- 需要检查 `lastSubscriptionStatus.status` 判断订阅状态
- 支持展示订阅管理页面 `showManagedSubscriptions`

```typescript
import { iap } from '@kit.IAPKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { common } from '@kit.AbilityKit';
import { promptAction } from '@kit.ArkUI';
import { JWSUtil } from '../common/JWSUtil';
import Logger from '../common/Logger';
import {
  FinishStatus, PurchaseData, PurchaseOrderPayload,
  SubGroupStatusPayload, SubscriptionStatus, SubStatus
} from '../common/IapDataModel';

const TAG: string = 'SubscriptionsPage';

@Builder
export function SubscriptionsPageBuilder() {
  SubscriptionsPage();
}

@Entry
@Component
struct SubscriptionsPage {
  private context: common.UIAbilityContext = {} as common.UIAbilityContext;
  @State productInfoArray: ProductInfo[] = [];
  // ... 其他状态变量

  async onCase() {
    this.showLoadingPage();
    const queryEnvCode = await this.queryEnv();
    if (queryEnvCode !== 0) { /* 同上 */ return; }
    const queryProductsCode = await this.queryProducts();
    queryProductsCode === 0 && this.queryPurchases(iap.PurchaseQueryType.CURRENT_ENTITLEMENT);
  }

  queryPurchases(queryType: iap.PurchaseQueryType): Promise<void> {
    return new Promise<void>((resolve) => {
      const param: iap.QueryPurchasesParameter = {
        productType: iap.ProductType.AUTORENEWABLE,
        queryType: queryType
      };
      iap.queryPurchases(this.context, param).then((res: iap.QueryPurchaseResult) => {
        const purchaseDataList: string[] = res.purchaseDataList;
        if (!purchaseDataList || purchaseDataList.length <= 0) { resolve(); return; }
        for (let i = 0; i < purchaseDataList.length; i++) {
          this.dealPurchaseData(purchaseDataList[i]);
        }
        resolve();
      }).catch((err: BusinessError) => { resolve(); }).finally(() => { this.showNormalPage(); });
    });
  }

  // 关键差异: 使用jwsSubscriptionStatus
  dealPurchaseData(purchaseData: string) {
    try {
      const jwsSubscriptionStatus = (JSON.parse(purchaseData) as PurchaseData).jwsSubscriptionStatus;
      if (!jwsSubscriptionStatus) return;
      const subscriptionStatus = JWSUtil.decodeJwsObj(jwsSubscriptionStatus);
      if (!subscriptionStatus) return;
      const subGroupStatusPayload = JSON.parse(subscriptionStatus) as SubGroupStatusPayload;
      const lastSubscriptionStatus = subGroupStatusPayload.lastSubscriptionStatus;
      if (!lastSubscriptionStatus) return;

      // 检查订阅是否生效中
      if (lastSubscriptionStatus.status === SubStatus.ACTIVE) {
        const productId = lastSubscriptionStatus.renewalInfo?.productId;
        if (productId) {
          this.setProductInfoStatus(subGroupStatusPayload.subGroupId, productId, lastSubscriptionStatus.status);
        }
      }

      // 确认发货
      const purchaseOrderPayload = lastSubscriptionStatus.lastPurchaseOrder;
      if (purchaseOrderPayload && purchaseOrderPayload.finishStatus !== FinishStatus.FINISHED) {
        this.finishPurchase(purchaseOrderPayload);
      }
    } catch (e) { Logger.error(TAG, 'dealPurchaseData json error'); }
  }

  private setProductInfoStatus(groupId: string, productId: string, status: SubStatus) {
    for (let i = 0; i < this.productInfoArray.length; i++) {
      if (groupId !== this.productInfoArray[i].subscriptionInfo?.groupId) continue;
      const curProduct: ProductInfo = JSON.parse(JSON.stringify(this.productInfoArray[i]));
      curProduct.subStatus = this.productInfoArray[i].id === productId ? status : undefined;
      this.productInfoArray[i] = curProduct;
    }
  }

  queryProducts(): Promise<number> {
    return new Promise<number>((resolve) => {
      const queryProductParam: iap.QueryProductsParameter = {
        productType: iap.ProductType.AUTORENEWABLE,
        productIds: ['Sub001', 'KH003', 'FH301']  // 替换为实际商品ID
      };
      iap.queryProducts(this.context, queryProductParam).then((result) => {
        this.productInfoArray = result;
        resolve(0);
      }).catch((err: BusinessError) => { this.showFailedPage(); resolve(err.code); });
    });
  }

  subscribe(id: string, type: iap.ProductType, developerPayload: string) {
    try {
      const createPurchaseParam: iap.PurchaseParameter = { productId: id, productType: type , developerPayload: developerPayload};
      iap.createPurchase(this.context, createPurchaseParam).then((result) => {
        promptAction.openToast({ message: 'Succeeded in creating purchase.', duration: 2000 });
        this.dealPurchaseData(result.purchaseData);
      }).catch((err: BusinessError) => {
        promptAction.openToast({ message: `Failed: ${err.code}`, duration: 2000 });
        if (err.code === iap.IAPErrorCode.PRODUCT_OWNED || err.code === iap.IAPErrorCode.SYSTEM_ERROR) {
          this.queryPurchases(iap.PurchaseQueryType.UNFINISHED);
        }
      })
    } catch (err) { /* ... */ }
  }

  // 展示订阅管理页面
  private showSubManaged(windowMode: number, groupId: string = '') {
    const winParam: iap.UIWindowParameter = { windowScreenMode: windowMode };
    iap.showManagedSubscriptions(this.getUIContext().getHostContext(), winParam, groupId).then(() => {
      Logger.info(TAG, 'Succeeded in showing subscription page.');
    }).catch((err: BusinessError) => {
      Logger.error(TAG, `Failed to show subscription page. Code is ${err.code}, message is ${err.message}`);
    });
  }

  // UI中生效中的订阅点击跳转管理页，未生效的点击购买
  // onClick: item?.subStatus === SubStatus.ACTIVE ? showSubManaged(DIALOG_BOX, groupId) : subscribe(id, type)
  // 额外提供"Show Subscriptions List"按钮: showSubManaged(FULLSCREEN)
}

interface ProductInfo extends iap.Product {
  subStatus?: SubStatus;
}
```

## 非自动续期订阅页面 (NonRenewablesPage.ets)

与消耗型流程基本一致，主要差异：
- `productType` 使用 `iap.ProductType.NONRENEWABLE`
- `queryPurchases` 使用 `UNFINISHED` 查询

```typescript
// 关键参数差异
const param: iap.QueryPurchasesParameter = {
  productType: iap.ProductType.NONRENEWABLE,
  queryType: iap.PurchaseQueryType.UNFINISHED
};

const queryProductParam: iap.QueryProductsParameter = {
  productType: iap.ProductType.NONRENEWABLE,
  productIds: ['NA0001']  // 替换为实际商品ID
};
```

其余逻辑（环境检测、购买、处理购买数据、确认发货、防掉单）与消耗型完全相同。

## 路由配置 (route_map.json)

```json
{
  "routerMap": [
    { "name": "ConsumablesPage", "pageSourceFile": "src/main/ets/pages/ConsumablesPage.ets", "buildFunction": "ConsumablesPageBuilder" },
    { "name": "NonConsumablesPage", "pageSourceFile": "src/main/ets/pages/NonConsumablesPage.ets", "buildFunction": "NonConsumablesPageBuilder" },
    { "name": "SubscriptionsPage", "pageSourceFile": "src/main/ets/pages/SubscriptionsPage.ets", "buildFunction": "SubscriptionsPageBuilder" },
    { "name": "NonRenewablesPage", "pageSourceFile": "src/main/ets/pages/NonRenewablesPage.ets", "buildFunction": "NonRenewablesPageBuilder" }
  ]
}
```
