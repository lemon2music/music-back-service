package com.lemon.music.musicbackservice.iap.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemon.music.musicbackservice.common.BusinessException;
import com.lemon.music.musicbackservice.iap.domain.FulfillmentAction;
import com.lemon.music.musicbackservice.iap.domain.IapFulfillmentEntity;
import com.lemon.music.musicbackservice.iap.domain.IapNotificationLogEntity;
import com.lemon.music.musicbackservice.iap.domain.IapProductType;
import com.lemon.music.musicbackservice.iap.dto.payload.NotificationMetaData;
import com.lemon.music.musicbackservice.iap.dto.payload.NotificationPayload;
import com.lemon.music.musicbackservice.iap.dto.payload.PurchaseOrderPayload;
import com.lemon.music.musicbackservice.iap.dto.payload.SubGroupStatusPayload;
import com.lemon.music.musicbackservice.iap.mapper.IapFulfillmentMapper;
import com.lemon.music.musicbackservice.iap.mapper.IapNotificationLogMapper;
import com.lemon.music.musicbackservice.iap.support.JwsVerifier;
import com.lemon.music.musicbackservice.membership.service.MembershipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 关键事件通知：JWS 验签 + 幂等 + 按类型分发。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IapNotificationService {

    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    private final JwsVerifier jwsVerifier;
    private final ObjectMapper objectMapper;
    private final IapNotificationLogMapper notificationLogMapper;
    private final IapFulfillmentMapper fulfillmentMapper;
    private final IapFulfillmentService fulfillmentService;
    private final IapHuaweiClient huaweiClient;
    private final MembershipService membershipService;

    public void handleNotification(String jwsNotification) {
        NotificationPayload payload;
        try {
            String json = jwsVerifier.checkAndDecode(jwsNotification);
            payload = objectMapper.readValue(json, NotificationPayload.class);
        } catch (Exception e) {
            log.error("通知验签/解析失败", e);
            return; // 验签失败不落库，直接返回 200
        }

        // 幂等：先登记日志（唯一约束兜底重复投递）
        if (notificationLogMapper.findByRequestId(payload.notificationRequestId()) != null) {
            log.info("通知已处理，跳过 requestId={}", payload.notificationRequestId());
            return;
        }
        IapNotificationLogEntity logRow = toLogEntity(payload, jwsNotification, STATUS_PROCESSING);
        try {
            notificationLogMapper.insert(logRow);
        } catch (Exception dup) {
            log.info("通知重复投递，跳过 requestId={}", payload.notificationRequestId());
            return;
        }

        try {
            dispatch(payload);
            notificationLogMapper.updateStatus(logRow.getId(), STATUS_SUCCESS, null);
        } catch (Exception e) {
            log.error("通知处理失败 requestId={}", payload.notificationRequestId(), e);
            String msg = e.getMessage() == null ? "error" : e.getMessage();
            notificationLogMapper.updateStatus(logRow.getId(), STATUS_FAILED,
                    msg.substring(0, Math.min(500, msg.length())));
        }
    }

    private void dispatch(NotificationPayload payload) {
        NotificationMetaData m = payload.notificationMetaData();
        String type = payload.notificationType();
        switch (type) {
            case "DID_NEW_TRANSACTION" -> handleNewTransaction(m);
            case "REVOKE" -> handleRevoke(m);
            case "EXPIRE" -> handleExpire(m);
            case "DID_CHANGE_RENEWAL_STATUS", "RENEWAL_TIME_MODIFIED" ->
                    log.info("通知仅记录，不改动权益: type={}", type);
            default -> log.warn("未知通知类型: {}", type);
        }
    }

    private void handleNewTransaction(NotificationMetaData m) {
        IapProductType pt = IapProductType.fromCode(m.type());
        PurchaseOrderPayload order = queryAndDecodeOrder(pt, m.purchaseOrderId(), m.purchaseToken());
        boolean granted = fulfillmentService.grantFromOrderPayload(order);
        if (granted) {
            if (pt == IapProductType.AUTORENEWABLE) {
                huaweiClient.subShippedConfirm(order.purchaseOrderId(), order.purchaseToken());
            } else {
                huaweiClient.orderShippedConfirm(order.purchaseOrderId(), order.purchaseToken());
            }
        }
    }

    private void handleRevoke(NotificationMetaData m) {
        IapProductType pt = IapProductType.fromCode(m.type());
        PurchaseOrderPayload order = queryAndDecodeOrder(pt, m.purchaseOrderId(), m.purchaseToken());
        fulfillmentService.revokeByPurchaseOrder(order);
    }

    private void handleExpire(NotificationMetaData m) {
        IapFulfillmentEntity grant = fulfillmentMapper.findByPurchaseOrderIdAndAction(
                m.purchaseOrderId(), FulfillmentAction.GRANT);
        if (grant != null) {
            membershipService.revokeSubscription(grant.getUserId());
        }
    }

    private PurchaseOrderPayload queryAndDecodeOrder(IapProductType pt, String purchaseOrderId, String purchaseToken) {
        try {
            if (pt == IapProductType.AUTORENEWABLE) {
                var resp = huaweiClient.subStatusQuery(purchaseOrderId, purchaseToken);
                String json = jwsVerifier.checkAndDecode(resp.jwsSubGroupStatus());
                SubGroupStatusPayload sub = objectMapper.readValue(json, SubGroupStatusPayload.class);
                return sub.lastSubscriptionStatus().lastPurchaseOrder();
            }
            var resp = huaweiClient.orderStatusQuery(purchaseOrderId, purchaseToken);
            String json = jwsVerifier.checkAndDecode(resp.jwsPurchaseOrder());
            return objectMapper.readValue(json, PurchaseOrderPayload.class);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("查询/验签订单状态失败");
        }
    }

    private IapNotificationLogEntity toLogEntity(NotificationPayload p, String rawJws, String status) {
        IapNotificationLogEntity e = new IapNotificationLogEntity();
        e.setNotificationRequestId(p.notificationRequestId());
        e.setNotificationType(p.notificationType());
        e.setNotificationSubtype(p.notificationSubtype());
        NotificationMetaData m = p.notificationMetaData();
        if (m != null) {
            e.setHuaweiPurchaseOrderId(m.purchaseOrderId());
            e.setHuaweiPurchaseToken(m.purchaseToken());
            e.setHuaweiProductId(m.currentProductId());
        }
        e.setRawJws(rawJws);
        e.setStatus(status);
        e.setCreatedAt(LocalDateTime.now());
        return e;
    }
}
