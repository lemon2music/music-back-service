package com.lemon.music.musicbackservice.membership.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户购买/持有商品记录。
 * 消耗性商品 used 标记是否已使用；非消耗性商品购买后即视为已使用（used=true）。
 */
@Data
public class UserProductEntity {

    private Long id;
    private Long userId;
    private Long productId;
    private LocalDateTime purchaseTime;
    private Boolean used;
    private LocalDateTime usedTime;
}
