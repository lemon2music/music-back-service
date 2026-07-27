package com.lemon.music.musicbackservice.iap.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemon.music.musicbackservice.common.BusinessException;
import com.lemon.music.musicbackservice.iap.domain.FulfillmentAction;
import com.lemon.music.musicbackservice.iap.domain.IapFulfillmentEntity;
import com.lemon.music.musicbackservice.iap.domain.IapOrderEntity;
import com.lemon.music.musicbackservice.iap.domain.IapProductEntity;
import com.lemon.music.musicbackservice.iap.domain.IapProductType;
import com.lemon.music.musicbackservice.iap.domain.OrderStatus;
import com.lemon.music.musicbackservice.iap.dto.payload.PurchaseData;
import com.lemon.music.musicbackservice.iap.dto.payload.PurchaseOrderPayload;
import com.lemon.music.musicbackservice.iap.dto.payload.SubGroupStatusPayload;
import com.lemon.music.musicbackservice.iap.dto.response.ReportPurchaseResponse;
import com.lemon.music.musicbackservice.iap.mapper.IapFulfillmentMapper;
import com.lemon.music.musicbackservice.iap.mapper.IapOrderMapper;
import com.lemon.music.musicbackservice.iap.mapper.IapProductMapper;
import com.lemon.music.musicbackservice.iap.support.JwsVerifier;
import com.lemon.music.musicbackservice.membership.domain.MembershipType;
import com.lemon.music.musicbackservice.membership.domain.PointsChangeReason;
import com.lemon.music.musicbackservice.membership.domain.ProductEntity;
import com.lemon.music.musicbackservice.membership.mapper.ProductMapper;
import com.lemon.music.musicbackservice.membership.service.MembershipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 验签发货 / 幂等 / 退款回收 —— IAP 的核心翻译层。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IapFulfillmentService {

    private final JwsVerifier jwsVerifier;
    private final ObjectMapper objectMapper;
    private final IapOrderMapper iapOrderMapper;
    private final IapFulfillmentMapper fulfillmentMapper;
    private final IapProductMapper iapProductMapper;
    private final ProductMapper productMapper;
    private final MembershipService membershipService;

    // ==================== 客户端上报（购买 + 补单）====================

    @Transactional
    public ReportPurchaseResponse handleReport(Long userId, IapProductType type, String purchaseData) {
        PurchaseData data = parse(purchaseData, PurchaseData.class);
        PurchaseOrderPayload order = resolveOrderPayload(type, data);

        IapOrderEntity iapOrder = iapOrderMapper.findByOrderNo(order.developerPayload());
        if (iapOrder == null || !iapOrder.getUserId().equals(userId)) {
            throw new BusinessException("上报数据与业务订单不匹配");
        }
        boolean fulfilled = grantFromOrderPayload(order);
        return new ReportPurchaseResponse(fulfilled, iapOrder.getId(),
                fulfilled ? "发货成功" : "无需重复发货");
    }

    // ==================== 发放（购买/补单/新交易通知 共用）====================

    @Transactional
    public boolean grantFromOrderPayload(PurchaseOrderPayload order) {
        if (isBlank(order.developerPayload())) {
            log.warn("grant: developerPayload 为空，purchaseOrderId={}", order.purchaseOrderId());
            return false;
        }
        if (fulfillmentMapper.findByPurchaseOrderIdAndAction(order.purchaseOrderId(), FulfillmentAction.GRANT) != null) {
            log.info("grant: 已发放，跳过 purchaseOrderId={}", order.purchaseOrderId());
            return false;
        }
        if (!isBlank(order.purchaseOrderRevocationReasonCode())) {
            log.info("grant: 订单已退款，不发货 purchaseOrderId={}", order.purchaseOrderId());
            return false;
        }
        IapOrderEntity iapOrder = iapOrderMapper.findByOrderNo(order.developerPayload());
        if (iapOrder == null) {
            log.warn("grant: 业务订单不存在 orderNo={}", order.developerPayload());
            return false;
        }
        IapProductEntity product = iapProductMapper.findById(iapOrder.getIapProductId());
        int pointsGranted = grantEntitlement(iapOrder.getUserId(), product);

        IapFulfillmentEntity f = new IapFulfillmentEntity();
        f.setHuaweiPurchaseOrderId(order.purchaseOrderId());
        f.setHuaweiPurchaseToken(order.purchaseToken());
        f.setIapOrderId(iapOrder.getId());
        f.setUserId(iapOrder.getUserId());
        f.setIapProductId(product.getId());
        f.setIapProductType(product.getIapProductType());
        f.setAction(FulfillmentAction.GRANT);
        f.setPointsGranted(pointsGranted);
        f.setEffectSnapshot(productEffectSnapshot(product));
        f.setCreatedAt(LocalDateTime.now());
        fulfillmentMapper.insert(f);

        LocalDateTime now = LocalDateTime.now();
        iapOrderMapper.updateFulfillment(iapOrder.getId(), order.purchaseOrderId(), order.purchaseToken(),
                OrderStatus.FULFILLED, now, now, now);
        return true;
    }

    // ==================== 回收（退款通知 共用）====================

    @Transactional
    public void revokeByPurchaseOrder(PurchaseOrderPayload order) {
        IapFulfillmentEntity grant =
                fulfillmentMapper.findByPurchaseOrderIdAndAction(order.purchaseOrderId(), FulfillmentAction.GRANT);
        if (grant == null) {
            log.warn("revoke: 无发放记录，跳过 purchaseOrderId={}", order.purchaseOrderId());
            return;
        }
        if (fulfillmentMapper.findByPurchaseOrderIdAndAction(order.purchaseOrderId(), FulfillmentAction.REVOKE) != null) {
            log.info("revoke: 已回收，跳过 purchaseOrderId={}", order.purchaseOrderId());
            return;
        }
        IapProductEntity product = iapProductMapper.findById(grant.getIapProductId());
        reverseEntitlement(grant.getUserId(), product, grant.getPointsGranted());

        IapFulfillmentEntity r = new IapFulfillmentEntity();
        r.setHuaweiPurchaseOrderId(order.purchaseOrderId());
        r.setHuaweiPurchaseToken(order.purchaseToken());
        r.setIapOrderId(grant.getIapOrderId());
        r.setUserId(grant.getUserId());
        r.setIapProductId(product.getId());
        r.setIapProductType(product.getIapProductType());
        r.setAction(FulfillmentAction.REVOKE);
        r.setPointsGranted(0);
        r.setEffectSnapshot(productEffectSnapshot(product));
        r.setCreatedAt(LocalDateTime.now());
        fulfillmentMapper.insert(r);

        iapOrderMapper.updateStatus(grant.getIapOrderId(), OrderStatus.REFUNDED, LocalDateTime.now());
    }

    // ==================== 内部：凭证解析 / 发放 / 回收 ====================

    private PurchaseOrderPayload resolveOrderPayload(IapProductType type, PurchaseData data) {
        try {
            if (type == IapProductType.AUTORENEWABLE) {
                String json = jwsVerifier.checkAndDecode(data.jwsSubscriptionStatus());
                SubGroupStatusPayload sub = objectMapper.readValue(json, SubGroupStatusPayload.class);
                if (sub.lastSubscriptionStatus() == null
                        || sub.lastSubscriptionStatus().lastPurchaseOrder() == null) {
                    throw new BusinessException("订阅状态数据缺失");
                }
                return sub.lastSubscriptionStatus().lastPurchaseOrder();
            }
            String json = jwsVerifier.checkAndDecode(data.jwsPurchaseOrder());
            return objectMapper.readValue(json, PurchaseOrderPayload.class);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("PurchaseData 解析/验签失败");
        }
    }

    private int grantEntitlement(Long userId, IapProductEntity product) {
        return switch (product.getIapProductType()) {
            case CONSUMABLE, NON_CONSUMABLE -> applyProductEffect(userId, product);
            case NONRENEWABLE, AUTORENEWABLE -> {
                membershipService.purchaseSubscription(userId, product.getSubscriptionType());
                yield 0;
            }
        };
    }

    private void reverseEntitlement(Long userId, IapProductEntity product, int pointsGranted) {
        switch (product.getIapProductType()) {
            case CONSUMABLE, NON_CONSUMABLE -> {
                ProductEntity p = productMapper.findById(product.getInternalRefId());
                String effectType = parse(p.getEffectConfig(), JsonNode.class).path("type").asText("");
                if ("POINTS_BONUS".equals(effectType)) {
                    membershipService.deductPoints(userId, pointsGranted, PointsChangeReason.IAP_REVOKE, "IAP退款回收");
                } else if ("MEMBERSHIP_UPGRADE".equals(effectType)) {
                    membershipService.downgradeMembershipType(userId, MembershipType.VIP);
                }
            }
            case NONRENEWABLE, AUTORENEWABLE -> membershipService.revokeSubscription(userId);
        }
    }

    private int applyProductEffect(Long userId, IapProductEntity iapProduct) {
        ProductEntity product = productMapper.findById(iapProduct.getInternalRefId());
        JsonNode cfg = parse(product.getEffectConfig(), JsonNode.class);
        String type = cfg.path("type").asText("");
        return switch (type) {
            case "POINTS_BONUS" -> {
                int pts = cfg.path("points").asInt(0);
                if (pts <= 0) {
                    throw new BusinessException("商品积分配置无效");
                }
                membershipService.addPoints(userId, pts, PointsChangeReason.PRODUCT_REDEEM,
                        "IAP购买:" + product.getProductName());
                yield pts;
            }
            case "MEMBERSHIP_UPGRADE" -> {
                membershipService.upgradeMembershipType(userId,
                        MembershipType.valueOf(cfg.path("membershipType").asText("VIP")));
                yield 0;
            }
            default -> throw new BusinessException("未知商品效果: " + type);
        };
    }

    private String productEffectSnapshot(IapProductEntity product) {
        if (product.getIapProductType() == IapProductType.CONSUMABLE
                || product.getIapProductType() == IapProductType.NON_CONSUMABLE) {
            ProductEntity p = productMapper.findById(product.getInternalRefId());
            return p.getEffectConfig();
        }
        return "{\"subscriptionType\":\"" + product.getSubscriptionType() + "\"}";
    }

    private <T> T parse(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new BusinessException("JSON 解析失败: " + type.getSimpleName());
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
