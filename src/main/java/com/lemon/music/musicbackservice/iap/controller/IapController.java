package com.lemon.music.musicbackservice.iap.controller;

import com.lemon.music.musicbackservice.common.ApiResponse;
import com.lemon.music.musicbackservice.iap.dto.response.IapProductResponse;
import com.lemon.music.musicbackservice.iap.service.IapProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/iap")
@RequiredArgsConstructor
public class IapController {

    private final IapProductService iapProductService;

    /** 查询上架的 IAP 商品列表（登录即可）。 */
    @GetMapping("/products")
    public ApiResponse<List<IapProductResponse>> getProducts() {
        return ApiResponse.ok(iapProductService.getAvailableProducts());
    }
}
