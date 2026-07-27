package com.lemon.music.musicbackservice.iap.controller;

import com.lemon.music.musicbackservice.auth.AuthContext;
import com.lemon.music.musicbackservice.auth.RequirePermission;
import com.lemon.music.musicbackservice.common.ApiResponse;
import com.lemon.music.musicbackservice.iap.dto.request.CreateOrderRequest;
import com.lemon.music.musicbackservice.iap.dto.request.ReportPurchaseRequest;
import com.lemon.music.musicbackservice.iap.dto.response.IapProductResponse;
import com.lemon.music.musicbackservice.iap.dto.response.PreOrderResponse;
import com.lemon.music.musicbackservice.iap.dto.response.ReportPurchaseResponse;
import com.lemon.music.musicbackservice.iap.service.IapFulfillmentService;
import com.lemon.music.musicbackservice.iap.service.IapOrderService;
import com.lemon.music.musicbackservice.iap.service.IapProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/iap")
@RequiredArgsConstructor
public class IapController {

    private final IapProductService iapProductService;
    private final IapOrderService iapOrderService;
    private final IapFulfillmentService iapFulfillmentService;

    /** 查询上架的 IAP 商品列表（登录即可）。 */
    @GetMapping("/products")
    public ApiResponse<List<IapProductResponse>> getProducts() {
        return ApiResponse.ok(iapProductService.getAvailableProducts());
    }

    /** 预下单（登录，需 IAP_PURCHASE 权限）。 */
    @PostMapping("/orders")
    @RequirePermission("IAP_PURCHASE")
    public ApiResponse<PreOrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        Long userId = AuthContext.get().userId();
        return ApiResponse.ok(iapOrderService.createOrder(userId, request.iapProductId()));
    }

    /** 上报 purchaseData，服务端验签发放权益（登录，需 IAP_PURCHASE）。购买与补单共用。 */
    @PostMapping("/orders/report")
    @RequirePermission("IAP_PURCHASE")
    public ApiResponse<ReportPurchaseResponse> reportPurchase(@Valid @RequestBody ReportPurchaseRequest request) {
        Long userId = AuthContext.get().userId();
        return ApiResponse.ok(iapFulfillmentService.handleReport(userId, request.iapProductType(), request.purchaseData()));
    }
}
