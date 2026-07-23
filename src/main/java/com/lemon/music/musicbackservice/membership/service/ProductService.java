package com.lemon.music.musicbackservice.membership.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemon.music.musicbackservice.common.BusinessException;
import com.lemon.music.musicbackservice.membership.domain.MembershipType;
import com.lemon.music.musicbackservice.membership.domain.PointsChangeReason;
import com.lemon.music.musicbackservice.membership.domain.ProductEntity;
import com.lemon.music.musicbackservice.membership.domain.ProductType;
import com.lemon.music.musicbackservice.membership.domain.UserProductEntity;
import com.lemon.music.musicbackservice.membership.mapper.ProductMapper;
import com.lemon.music.musicbackservice.membership.mapper.UserProductMapper;
import com.lemon.music.musicbackservice.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品业务逻辑：商品列表、购买（消耗性/非消耗性）、使用消耗性商品。
 *
 * <p>商品效果通过 effect_config (JSON) 描述：
 * <ul>
 *   <li>{@code {"type":"POINTS_BONUS","points":1000}} —— 增加积分</li>
 *   <li>{@code {"type":"MEMBERSHIP_UPGRADE","membershipType":"SVIP"}} —— 升级会员类型</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductMapper productMapper;
    private final UserProductMapper userProductMapper;
    private final MembershipService membershipService;
    private final ObjectMapper objectMapper;
    private final UserMapper userMapper;

    /** 获取所有上架商品。 */
    public List<ProductEntity> getAvailableProducts() {
        return productMapper.findActive();
    }

    /**
     * 购买商品。消耗性商品入库待使用；非消耗性商品立即生效且只能购买一次。
     */
    @Transactional
    public void purchaseProduct(Long userId, Long productId) {
        ProductEntity product = productMapper.findById(productId);
        if (product == null) {
            throw new BusinessException("商品不存在");
        }
        if (!"ACTIVE".equals(product.getStatus())) {
            throw new BusinessException("商品已下架");
        }

        if (product.getProductType() == ProductType.CONSUMABLE) {
            purchaseConsumableProduct(userId, product);
        } else {
            purchaseNonConsumableProduct(userId, product);
        }
    }

    /**
     * 使用消耗性商品：校验所有权与未使用状态，应用效果后标记已使用。
     */
    @Transactional
    public void useConsumableProduct(Long userId, Long userProductId) {
        UserProductEntity userProduct = userProductMapper.findById(userProductId);
        if (userProduct == null) {
            throw new BusinessException("购买记录不存在");
        }
        if (!userProduct.getUserId().equals(userId)) {
            throw new BusinessException("无权使用该商品");
        }
        if (Boolean.TRUE.equals(userProduct.getUsed())) {
            throw new BusinessException("该商品已使用");
        }

        ProductEntity product = productMapper.findById(userProduct.getProductId());
        if (product == null) {
            throw new BusinessException("商品不存在");
        }

        applyEffect(userId, product);

        userProduct.setUsed(true);
        userProduct.setUsedTime(LocalDateTime.now());
        userProductMapper.update(userProduct);
    }

    // ==================== 内部方法 ====================

    private void purchaseConsumableProduct(Long userId, ProductEntity product) {
        LocalDateTime now = LocalDateTime.now();
        UserProductEntity userProduct = new UserProductEntity();
        userProduct.setUserId(userId);
        userProduct.setProductId(product.getId());
        userProduct.setPurchaseTime(now);
        userProduct.setUsed(false);
        userProduct.setUsedTime(null);
        userProductMapper.insert(userProduct);
    }

    private void purchaseNonConsumableProduct(Long userId, ProductEntity product) {
        // 锁定用户行（持有至事务提交），串行化同一用户的并发购买，
        // 避免两个并发请求同时通过重复检查后各自写入。
        userMapper.lockById(userId);
        // 非消耗性商品只能购买一次
        if (!userProductMapper.findByUserIdAndProductId(userId, product.getId()).isEmpty()) {
            throw new BusinessException("该商品只能购买一次");
        }

        applyEffect(userId, product);

        LocalDateTime now = LocalDateTime.now();
        UserProductEntity userProduct = new UserProductEntity();
        userProduct.setUserId(userId);
        userProduct.setProductId(product.getId());
        userProduct.setPurchaseTime(now);
        userProduct.setUsed(true); // 非消耗性商品购买即生效
        userProduct.setUsedTime(now);
        userProductMapper.insert(userProduct);
    }

    /** 解析并应用商品效果。 */
    private void applyEffect(Long userId, ProductEntity product) {
        JsonNode effectConfig = parseEffectConfig(product);
        String effectType = effectConfig.path("type").asText("");

        switch (effectType) {
            case "POINTS_BONUS" -> {
                int points = effectConfig.path("points").asInt(0);
                if (points <= 0) {
                    throw new BusinessException("商品积分配置无效");
                }
                membershipService.addPoints(userId, points, PointsChangeReason.PRODUCT_REDEEM,
                        "使用商品: " + product.getProductName());
            }
            case "MEMBERSHIP_UPGRADE" -> {
                String typeStr = effectConfig.path("membershipType").asText("");
                if (typeStr.isEmpty()) {
                    throw new BusinessException("商品会员类型配置无效");
                }
                membershipService.upgradeMembershipType(userId, MembershipType.valueOf(typeStr));
            }
            default -> throw new BusinessException("未知的商品效果类型: " + effectType);
        }
    }

    private JsonNode parseEffectConfig(ProductEntity product) {
        try {
            return objectMapper.readTree(product.getEffectConfig());
        } catch (Exception e) {
            log.error("解析商品效果配置失败, productId={}", product.getId(), e);
            throw new BusinessException("商品效果配置解析失败");
        }
    }
}
