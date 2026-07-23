package com.lemon.music.musicbackservice.membership.domain;

/**
 * 积分变化原因
 */
public enum PointsChangeReason {
    /** 每日增长（有会员资格） */
    DAILY_GAIN,
    /** 每日扣减（失去会员资格） */
    DAILY_LOSS,
    /** 商品兑换 */
    PRODUCT_REDEEM,
    /** 订阅购买 */
    SUBSCRIPTION_PURCHASE,
    /** 订阅到期 */
    SUBSCRIPTION_EXPIRE
}
