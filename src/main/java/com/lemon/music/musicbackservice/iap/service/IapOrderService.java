package com.lemon.music.musicbackservice.iap.service;

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
}
