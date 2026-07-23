package com.lemon.music.musicbackservice.membership.controller;

import com.lemon.music.musicbackservice.auth.AuthContext;
import com.lemon.music.musicbackservice.auth.RequirePermission;
import com.lemon.music.musicbackservice.common.ApiResponse;
import com.lemon.music.musicbackservice.membership.domain.MembershipEntity;
import com.lemon.music.musicbackservice.membership.domain.MembershipPointsHistoryEntity;
import com.lemon.music.musicbackservice.membership.domain.ProductEntity;
import com.lemon.music.musicbackservice.membership.dto.request.PurchaseProductRequest;
import com.lemon.music.musicbackservice.membership.dto.request.PurchaseSubscriptionRequest;
import com.lemon.music.musicbackservice.membership.dto.response.MembershipInfoResponse;
import com.lemon.music.musicbackservice.membership.dto.response.PointsHistoryResponse;
import com.lemon.music.musicbackservice.membership.dto.response.ProductInfoResponse;
import com.lemon.music.musicbackservice.membership.service.MembershipService;
import com.lemon.music.musicbackservice.membership.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会员系统 API。
 *
 * <p>查询接口（info/points-history/products）需登录但不需特定权限；
 * 写接口（subscribe/products 购买与使用）需对应权限。
 */
@RestController
@RequestMapping("/api/membership")
@RequiredArgsConstructor
public class MembershipController {

    private final MembershipService membershipService;
    private final ProductService productService;

    /** 查询当前用户会员基本信息（积分、等级、会员类型、资格状态）。 */
    @GetMapping("/info")
    public ApiResponse<MembershipInfoResponse> getMembershipInfo() {
        Long userId = AuthContext.get().userId();
        MembershipEntity membership = membershipService.getMembershipByUserId(userId);
        String nextLevelInfo = membershipService.calculateNextLevelInfo(
                membership.getCurrentPoints(), membership.getVipLevel());

        MembershipInfoResponse response = new MembershipInfoResponse(
                membership.getUserId(),
                membership.getCurrentPoints(),
                membership.getMembershipType(),
                membership.getVipLevel(),
                membership.getHasMembership(),
                membership.getSubscriptionExpireAt(),
                nextLevelInfo,
                membership.getCreatedAt(),
                membership.getUpdatedAt()
        );
        return ApiResponse.ok(response);
    }

    /** 查询当前用户积分历史（分页，按时间倒序）。 */
    @GetMapping("/points-history")
    public ApiResponse<List<PointsHistoryResponse>> getPointsHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = AuthContext.get().userId();
        List<MembershipPointsHistoryEntity> historyList =
                membershipService.getPointsHistory(userId, page, size);

        List<PointsHistoryResponse> responseList = historyList.stream()
                .map(h -> new PointsHistoryResponse(
                        h.getId(),
                        h.getPointsChange(),
                        h.getPointsBefore(),
                        h.getPointsAfter(),
                        h.getChangeReason(),
                        h.getDescription(),
                        h.getCreatedAt()))
                .toList();
        return ApiResponse.ok(responseList);
    }

    /** 购买会员订阅（月卡/季卡/年卡）。 */
    @PostMapping("/subscribe")
    @RequirePermission("MEMBERSHIP_PURCHASE")
    public ApiResponse<Void> purchaseSubscription(@Valid @RequestBody PurchaseSubscriptionRequest request) {
        Long userId = AuthContext.get().userId();
        membershipService.purchaseSubscription(userId, request.subscriptionType());
        return ApiResponse.ok("subscribe success", null);
    }

    /** 获取上架商品列表。 */
    @GetMapping("/products")
    public ApiResponse<List<ProductInfoResponse>> getAvailableProducts() {
        List<ProductEntity> products = productService.getAvailableProducts();
        List<ProductInfoResponse> responseList = products.stream()
                .map(p -> new ProductInfoResponse(
                        p.getId(),
                        p.getProductName(),
                        p.getProductType(),
                        p.getPrice(),
                        p.getDescription(),
                        true))
                .toList();
        return ApiResponse.ok(responseList);
    }

    /** 购买商品（消耗性入库待用；非消耗性立即生效）。 */
    @PostMapping("/products/purchase")
    @RequirePermission("PRODUCT_PURCHASE")
    public ApiResponse<Void> purchaseProduct(@Valid @RequestBody PurchaseProductRequest request) {
        Long userId = AuthContext.get().userId();
        productService.purchaseProduct(userId, request.productId());
        return ApiResponse.ok("purchase success", null);
    }

    /** 使用已购买的消耗性商品。 */
    @PostMapping("/products/{userProductId}/use")
    @RequirePermission("PRODUCT_USE")
    public ApiResponse<Void> useProduct(@PathVariable Long userProductId) {
        Long userId = AuthContext.get().userId();
        productService.useConsumableProduct(userId, userProductId);
        return ApiResponse.ok("use product success", null);
    }
}
