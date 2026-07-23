package com.lemon.music.musicbackservice.iap.dto.request;

import com.lemon.music.musicbackservice.iap.domain.IapProductType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReportPurchaseRequest(
        @NotNull(message = "商品类型不能为空")
        IapProductType iapProductType,
        @NotBlank(message = "purchaseData 不能为空")
        String purchaseData
) {}
