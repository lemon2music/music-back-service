package com.lemon.music.musicbackservice.membership.domain;

/**
 * VIP 等级，按积分区间划分：
 * <ul>
 *     <li>VIP1: 1-1000</li>
 *     <li>VIP2: 1001-3000</li>
 *     <li>VIP3: 3001-10000</li>
 *     <li>VIP4: 10001-50000</li>
 *     <li>VIP5: 50001+</li>
 * </ul>
 */
public enum VipLevel {
    VIP1,
    VIP2,
    VIP3,
    VIP4,
    VIP5
}
