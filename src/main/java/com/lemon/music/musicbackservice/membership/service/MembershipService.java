package com.lemon.music.musicbackservice.membership.service;

import com.lemon.music.musicbackservice.common.BusinessException;
import com.lemon.music.musicbackservice.membership.domain.MembershipEntity;
import com.lemon.music.musicbackservice.membership.domain.MembershipPointsHistoryEntity;
import com.lemon.music.musicbackservice.membership.domain.MembershipSubscriptionEntity;
import com.lemon.music.musicbackservice.membership.domain.MembershipType;
import com.lemon.music.musicbackservice.membership.domain.PointsChangeReason;
import com.lemon.music.musicbackservice.membership.domain.SubscriptionType;
import com.lemon.music.musicbackservice.membership.domain.VipLevel;
import com.lemon.music.musicbackservice.membership.mapper.MembershipMapper;
import com.lemon.music.musicbackservice.membership.mapper.MembershipPointsHistoryMapper;
import com.lemon.music.musicbackservice.membership.mapper.MembershipSubscriptionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会员核心业务逻辑：积分、等级、订阅资格。
 *
 * <p>等级与积分对应（下界包含）：
 * <ul>
 *   <li>VIP1: 0 - 1000</li>
 *   <li>VIP2: 1001 - 3000</li>
 *   <li>VIP3: 3001 - 10000</li>
 *   <li>VIP4: 10001 - 49999</li>
 *   <li>VIP5: 50000+</li>
 * </ul>
 */
@Slf4j
@Service
public class MembershipService {

    private final MembershipMapper membershipMapper;
    private final MembershipPointsHistoryMapper pointsHistoryMapper;
    private final MembershipSubscriptionMapper subscriptionMapper;
    private final TransactionTemplate transactionTemplate;

    public MembershipService(MembershipMapper membershipMapper,
                             MembershipPointsHistoryMapper pointsHistoryMapper,
                             MembershipSubscriptionMapper subscriptionMapper,
                             PlatformTransactionManager transactionManager) {
        this.membershipMapper = membershipMapper;
        this.pointsHistoryMapper = pointsHistoryMapper;
        this.subscriptionMapper = subscriptionMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // ==================== 初始化与查询 ====================

    /**
     * 为新用户初始化会员信息（VIP1、0 积分、无会员资格）。
     * 用户注册时调用。
     */
    public void initializeMembership(Long userId) {
        if (membershipMapper.findByUserId(userId) != null) {
            return; // 已存在，幂等
        }
        LocalDateTime now = LocalDateTime.now();
        MembershipEntity membership = new MembershipEntity();
        membership.setUserId(userId);
        membership.setCurrentPoints(0);
        membership.setMembershipType(MembershipType.VIP);
        membership.setVipLevel(VipLevel.VIP1);
        membership.setHasMembership(false);
        membership.setSubscriptionExpireAt(null);
        membership.setCreatedAt(now);
        membership.setUpdatedAt(now);
        membershipMapper.insert(membership);
    }

    /** 获取会员信息，不存在则抛业务异常。 */
    public MembershipEntity getMembershipByUserId(Long userId) {
        MembershipEntity membership = membershipMapper.findByUserId(userId);
        if (membership == null) {
            throw new BusinessException("用户会员信息不存在");
        }
        return membership;
    }

    /** 分页查询积分历史（按时间倒序）。 */
    public List<MembershipPointsHistoryEntity> getPointsHistory(Long userId, int page, int size) {
        int offset = Math.max(0, page) * Math.max(1, size);
        int limit = Math.max(1, size);
        return pointsHistoryMapper.findByUserIdWithPaging(userId, offset, limit);
    }

    /** 计算下一等级提示信息。 */
    public String calculateNextLevelInfo(int currentPoints, VipLevel currentLevel) {
        return switch (currentLevel) {
            case VIP1 -> "距离VIP2还需" + Math.max(0, 1001 - currentPoints) + "积分";
            case VIP2 -> "距离VIP3还需" + Math.max(0, 3001 - currentPoints) + "积分";
            case VIP3 -> "距离VIP4还需" + Math.max(0, 10001 - currentPoints) + "积分";
            case VIP4 -> "距离VIP5还需" + Math.max(0, 50000 - currentPoints) + "积分";
            case VIP5 -> "已达到最高等级";
        };
    }

    // ==================== 订阅 ====================

    /**
     * 购买会员订阅：获得会员资格，更新到期时间。
     * 若当前仍有有效订阅，则在原到期时间基础上续期。
     */
    public void purchaseSubscription(Long userId, SubscriptionType type) {
        MembershipEntity membership = getMembershipByUserId(userId);

        int days = switch (type) {
            case MONTHLY -> 30;
            case QUARTERLY -> 90;
            case YEARLY -> 365;
        };

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime base = (membership.getSubscriptionExpireAt() != null
                && membership.getSubscriptionExpireAt().isAfter(now))
                ? membership.getSubscriptionExpireAt() : now;
        LocalDateTime expireTime = base.plusDays(days);

        membership.setHasMembership(true);
        membership.setSubscriptionExpireAt(expireTime);
        membership.setUpdatedAt(now);
        membershipMapper.update(membership);

        MembershipSubscriptionEntity subscription = new MembershipSubscriptionEntity();
        subscription.setUserId(userId);
        subscription.setSubscriptionType(type);
        subscription.setStartTime(now);
        subscription.setExpireTime(expireTime);
        subscription.setCreatedAt(now);
        subscriptionMapper.insert(subscription);
    }

    // ==================== 积分与等级 ====================

    /**
     * 增加积分（来自商品兑换等），并按需升级等级。
     * 变化记录到积分历史。
     */
    public void addPoints(Long userId, int points, PointsChangeReason reason, String description) {
        if (points == 0) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            MembershipEntity membership = membershipMapper.findByUserId(userId);
            if (membership == null) {
                throw new BusinessException("用户会员信息不存在");
            }
            int oldPoints = membership.getCurrentPoints();
            int newPoints = oldPoints + points;
            applyPointsChange(membership, oldPoints, newPoints, points, reason, description);
        });
    }

    /**
     * 升级会员类型（如购买终身顶级卡 → SVIP）。
     */
    public void upgradeMembershipType(Long userId, MembershipType newType) {
        MembershipEntity membership = getMembershipByUserId(userId);
        if (membership.getMembershipType() == newType) {
            throw new BusinessException("用户已经是该会员类型");
        }
        membership.setMembershipType(newType);
        membership.setUpdatedAt(LocalDateTime.now());
        membershipMapper.update(membership);
    }

    /**
     * 每日积分处理（定时任务调用）：
     * <ul>
     *   <li>订阅到期 → 取消会员资格</li>
     *   <li>有会员资格 → 按等级增加积分（SVIP 翻倍）</li>
     *   <li>无会员资格 → 按等级扣减积分（SVIP 翻倍）</li>
     * </ul>
     * 每个用户独立事务，单个用户失败不影响其他用户。
     */
    public void processDailyPointsChange() {
        List<MembershipEntity> memberships = membershipMapper.findAll();
        log.info("开始处理每日会员积分变化，共 {} 条会员记录", memberships.size());

        for (MembershipEntity membership : memberships) {
            try {
                transactionTemplate.executeWithoutResult(status -> processSingleMembership(membership));
            } catch (Exception e) {
                log.error("处理用户 {} 每日积分失败", membership.getUserId(), e);
            }
        }
        log.info("每日会员积分变化处理完成");
    }

    /** 处理单个会员的每日积分变化（运行在事务内）。 */
    private void processSingleMembership(MembershipEntity membership) {
        LocalDateTime now = LocalDateTime.now();

        // 1. 检查订阅是否到期
        if (Boolean.TRUE.equals(membership.getHasMembership())
                && membership.getSubscriptionExpireAt() != null
                && membership.getSubscriptionExpireAt().isBefore(now)) {
            handleSubscriptionExpire(membership);
            // 到期当次只取消资格，下一周期再扣减
            return;
        }

        // 2. 计算基础积分
        int basePoints = getBasePointsByLevel(membership.getVipLevel());
        if (membership.getMembershipType() == MembershipType.SVIP) {
            basePoints *= 2; // SVIP 翻倍
        }

        int oldPoints = membership.getCurrentPoints();
        int change;
        PointsChangeReason reason;
        String description;
        if (Boolean.TRUE.equals(membership.getHasMembership())) {
            change = basePoints;
            reason = PointsChangeReason.DAILY_GAIN;
            description = "每日积分增长";
        } else {
            change = -basePoints;
            reason = PointsChangeReason.DAILY_LOSS;
            description = "每日积分扣减（无会员资格）";
        }
        int newPoints = Math.max(0, oldPoints + change);
        applyPointsChange(membership, oldPoints, newPoints, change, reason, description);
    }

    /** 订阅到期处理：取消会员资格，记录历史。 */
    private void handleSubscriptionExpire(MembershipEntity membership) {
        int points = membership.getCurrentPoints();
        membership.setHasMembership(false);
        membership.setSubscriptionExpireAt(null);
        membership.setUpdatedAt(LocalDateTime.now());
        membershipMapper.update(membership);
        recordPointsHistory(membership.getUserId(), 0, points, points,
                PointsChangeReason.SUBSCRIPTION_EXPIRE, "订阅到期，取消会员资格");
    }

    /**
     * 应用积分变化：更新积分、记录历史、按需调整等级。
     * 调用方需保证已处于事务中。
     */
    private void applyPointsChange(MembershipEntity membership, int oldPoints, int newPoints,
                                   int change, PointsChangeReason reason, String description) {
        membership.setCurrentPoints(newPoints);
        membership.setUpdatedAt(LocalDateTime.now());
        membershipMapper.update(membership);

        recordPointsHistory(membership.getUserId(), change, oldPoints, newPoints, reason, description);

        VipLevel oldLevel = membership.getVipLevel();
        VipLevel newLevel = calculateLevelByPoints(newPoints);
        if (newLevel != oldLevel) {
            membership.setVipLevel(newLevel);
            membership.setUpdatedAt(LocalDateTime.now());
            membershipMapper.update(membership);
            log.info("用户 {} 等级变化: {} -> {}", membership.getUserId(), oldLevel, newLevel);
        }
    }

    private void recordPointsHistory(Long userId, int change, int before, int after,
                                     PointsChangeReason reason, String description) {
        MembershipPointsHistoryEntity history = new MembershipPointsHistoryEntity();
        history.setUserId(userId);
        history.setPointsChange(change);
        history.setPointsBefore(before);
        history.setPointsAfter(after);
        history.setChangeReason(reason);
        history.setDescription(description);
        history.setCreatedAt(LocalDateTime.now());
        pointsHistoryMapper.insert(history);
    }

    /** 根据积分计算等级（下界包含）。 */
    private VipLevel calculateLevelByPoints(int points) {
        if (points >= 50000) return VipLevel.VIP5;
        if (points >= 10001) return VipLevel.VIP4;
        if (points >= 3001) return VipLevel.VIP3;
        if (points >= 1001) return VipLevel.VIP2;
        return VipLevel.VIP1;
    }

    /** 各等级每日基础积分。 */
    private int getBasePointsByLevel(VipLevel level) {
        return switch (level) {
            case VIP1 -> 1;
            case VIP2 -> 2;
            case VIP3 -> 3;
            case VIP4 -> 4;
            case VIP5 -> 5;
        };
    }
}
