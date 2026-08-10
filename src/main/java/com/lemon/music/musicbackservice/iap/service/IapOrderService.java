package com.lemon.music.musicbackservice.iap.service;

import com.lemon.music.musicbackservice.common.BusinessException;
import com.lemon.music.musicbackservice.iap.domain.IapOrderEntity;
import com.lemon.music.musicbackservice.iap.domain.IapProductEntity;
import com.lemon.music.musicbackservice.iap.domain.OrderStatus;
import com.lemon.music.musicbackservice.iap.dto.response.PreOrderResponse;
import com.lemon.music.musicbackservice.iap.mapper.IapOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IapOrderService {

    private final IapOrderMapper iapOrderMapper;
    private final IapProductService iapProductService;

    /** 预下单：初始化业务订单，返回订单号作为端侧 developerPayload。 */
    @Transactional
    public PreOrderResponse createOrder(Long userId, Long iapProductId) {
        IapProductEntity product = iapProductService.getById(iapProductId);
        LocalDateTime now = LocalDateTime.now();

        IapOrderEntity order = new IapOrderEntity();
        order.setOrderNo("IAP" + UUID.randomUUID().toString().replace("-", ""));
        order.setUserId(userId);
        order.setIapProductId(product.getId());
        order.setHuaweiProductId(product.getHuaweiProductId());
        order.setAmount(product.getPrice());
        order.setCurrency(product.getCurrency());
        order.setStatus(OrderStatus.PENDING);
        order.setHuaweiPurchaseOrderId(null);
        order.setHuaweiPurchaseToken(null);
        order.setCreatedAt(now);
        order.setPaidAt(null);
        order.setFulfilledAt(null);
        order.setUpdatedAt(now);
        iapOrderMapper.insert(order);

        return new PreOrderResponse(order.getOrderNo(), product.getHuaweiProductId(),
                product.getIapProductType(), product.getPrice(), product.getCurrency());
    }

    /** 根据订单号查询订单，如果订单不存在则抛出异常。 */
    public IapOrderEntity findByOrderNo(String orderNo) {
        IapOrderEntity order = iapOrderMapper.findByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException("订单不存在: " + orderNo);
        }
        return order;
    }

    /**
     * 取消待支付订单
     * @param orderNo 订单号
     * @param cancelReason 取消原因（可为null）
     * @return 是否取消成功
     * @throws BusinessException 订单不存在或状态不允许取消
     */
    @Transactional
    public boolean cancelOrder(String orderNo, String cancelReason) {
        IapOrderEntity order = findByOrderNo(orderNo);

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException("只有PENDING状态的订单可以取消，当前状态: " + order.getStatus());
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = iapOrderMapper.cancelOrder(
                orderNo,
                OrderStatus.CLOSED,
                now,
                cancelReason != null ? cancelReason : "USER_CANCELLED",
                now
        );

        return updated > 0;
    }
}
