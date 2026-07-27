package com.lemon.music.musicbackservice.membership.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品。effect_config 为 JSON 字符串，描述商品效果：
 * <pre>
 * {"type":"POINTS_BONUS","points":1000}
 * {"type":"MEMBERSHIP_UPGRADE","membershipType":"SVIP"}
 * </pre>
 */
@Data
public class ProductEntity {

    private Long id;
    private String productName;
    private ProductType productType;
    private BigDecimal price;
    private String effectConfig;
    private String description;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
