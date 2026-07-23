package com.lemon.music.musicbackservice.membership;

import com.lemon.music.musicbackservice.membership.domain.MembershipEntity;
import com.lemon.music.musicbackservice.membership.domain.MembershipType;
import com.lemon.music.musicbackservice.membership.domain.PointsChangeReason;
import com.lemon.music.musicbackservice.membership.domain.SubscriptionType;
import com.lemon.music.musicbackservice.membership.domain.VipLevel;
import com.lemon.music.musicbackservice.membership.mapper.MembershipMapper;
import com.lemon.music.musicbackservice.membership.mapper.MembershipPointsHistoryMapper;
import com.lemon.music.musicbackservice.membership.service.MembershipService;
import com.lemon.music.musicbackservice.user.dto.RegisterRequest;
import com.lemon.music.musicbackservice.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 会员核心业务逻辑集成测试（H2）。
 * 验证积分规则、等级计算、SVIP 翻倍、订阅到期等核心逻辑。
 */
@SpringBootTest
@Transactional
class MembershipServiceIntegrationTest {

    @MockitoBean
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    private MembershipService membershipService;

    @Autowired
    private UserService userService;

    @Autowired
    private MembershipMapper membershipMapper;

    @Autowired
    private MembershipPointsHistoryMapper pointsHistoryMapper;

    @Test
    void shouldInitializeMembershipOnRegister() {
        Long userId = register("alice");

        MembershipEntity membership = membershipMapper.findByUserId(userId);
        assertThat(membership).isNotNull();
        assertThat(membership.getCurrentPoints()).isEqualTo(0);
        assertThat(membership.getMembershipType()).isEqualTo(MembershipType.VIP);
        assertThat(membership.getVipLevel()).isEqualTo(VipLevel.VIP1);
        assertThat(membership.getHasMembership()).isFalse();
    }

    @Test
    void shouldGainDailyPointsWhenHasMembership() {
        Long userId = register("bob");
        membershipService.purchaseSubscription(userId, SubscriptionType.MONTHLY);

        membershipService.processDailyPointsChange();

        MembershipEntity membership = membershipMapper.findByUserId(userId);
        // VIP1 有会员资格，每日 +1
        assertThat(membership.getCurrentPoints()).isEqualTo(1);
        assertThat(membership.getVipLevel()).isEqualTo(VipLevel.VIP1);
    }

    @Test
    void shouldLosePointsAndRecordHistoryWhenNoMembership() {
        Long userId = register("carol");
        // 无会员资格，每日扣减；0 积分时保持 0 但仍记录历史
        membershipService.processDailyPointsChange();

        MembershipEntity membership = membershipMapper.findByUserId(userId);
        assertThat(membership.getCurrentPoints()).isEqualTo(0); // 不会为负
        int historyCount = pointsHistoryMapper.countByUserId(userId);
        assertThat(historyCount).isEqualTo(1); // 记录了一次 DAILY_LOSS
    }

    @Test
    void shouldDoublePointsForSvip() {
        Long userId = register("dave");
        membershipService.purchaseSubscription(userId, SubscriptionType.MONTHLY);
        membershipService.upgradeMembershipType(userId, MembershipType.SVIP);

        membershipService.processDailyPointsChange();

        MembershipEntity membership = membershipMapper.findByUserId(userId);
        // SVIP + VIP1 = 1 * 2 = 2
        assertThat(membership.getMembershipType()).isEqualTo(MembershipType.SVIP);
        assertThat(membership.getCurrentPoints()).isEqualTo(2);
    }

    @Test
    void shouldUpgradeLevelWhenPointsReachThreshold() {
        Long userId = register("eve");
        membershipService.purchaseSubscription(userId, SubscriptionType.MONTHLY);

        // VIP1 (0积分) → 加 1001 积分 → VIP2
        membershipService.addPoints(userId, 1001, PointsChangeReason.PRODUCT_REDEEM, "test");

        MembershipEntity membership = membershipMapper.findByUserId(userId);
        assertThat(membership.getCurrentPoints()).isEqualTo(1001);
        assertThat(membership.getVipLevel()).isEqualTo(VipLevel.VIP2);
    }

    @Test
    void shouldUpgradeToVip5At50000Points() {
        Long userId = register("frank");
        membershipService.addPoints(userId, 50000, PointsChangeReason.PRODUCT_REDEEM, "test");

        MembershipEntity membership = membershipMapper.findByUserId(userId);
        assertThat(membership.getVipLevel()).isEqualTo(VipLevel.VIP5);
    }

    @Test
    void shouldKeepVip4Below50000Points() {
        Long userId = register("grace");
        membershipService.addPoints(userId, 49999, PointsChangeReason.PRODUCT_REDEEM, "test");

        MembershipEntity membership = membershipMapper.findByUserId(userId);
        assertThat(membership.getVipLevel()).isEqualTo(VipLevel.VIP4);
    }

    @Test
    void shouldExpireSubscriptionAndRevokeMembership() {
        Long userId = register("henry");
        membershipService.purchaseSubscription(userId, SubscriptionType.MONTHLY);
        MembershipEntity membership = membershipMapper.findByUserId(userId);
        assertThat(membership.getHasMembership()).isTrue();

        // 直接模拟到期：手动将到期时间设为过去
        membership.setSubscriptionExpireAt(membership.getSubscriptionExpireAt().minusDays(40));
        membershipMapper.update(membership);

        membershipService.processDailyPointsChange();

        MembershipEntity after = membershipMapper.findByUserId(userId);
        assertThat(after.getHasMembership()).isFalse();
        assertThat(after.getSubscriptionExpireAt()).isNull();
    }

    private Long register(String username) {
        return userService.register(new RegisterRequest(username, "password1", null));
    }
}
