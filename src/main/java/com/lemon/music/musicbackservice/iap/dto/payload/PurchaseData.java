package com.lemon.music.musicbackservice.iap.dto.payload;

/** createPurchase / queryPurchases 返回的 purchaseData 解析结构。 */
public record PurchaseData(
        Integer type,
        String jwsPurchaseOrder,
        String jwsSubscriptionStatus
) {}
