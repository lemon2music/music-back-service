package com.lemon.music.musicbackservice.iap.dto.request;

/**
 * IAP订单取消请求
 */
public record CancelOrderRequest(
    String cancelReason
) {}
