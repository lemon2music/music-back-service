package com.lemon.music.musicbackservice.membership;

import com.lemon.music.musicbackservice.membership.domain.MembershipEntity;
import com.lemon.music.musicbackservice.membership.domain.MembershipType;
import com.lemon.music.musicbackservice.membership.domain.PointsChangeReason;
import com.lemon.music.musicbackservice.membership.domain.SubscriptionType;
import com.lemon.music.musicbackservice.membership.mapper.MembershipMapper;
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

@SpringBootTest
@Transactional
class MembershipRevokeIntegrationTest {

    @MockitoBean
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    private MembershipService membershipService;
    @Autowired
    private UserService userService;
    @Autowired
    private MembershipMapper membershipMapper;

    @Test
    void deductPointsStopsAtZero() {
        Long userId = userService.register(new RegisterRequest("revoke1", "pw", null));
        membershipService.addPoints(userId, 100, PointsChangeReason.PRODUCT_REDEEM, "grant");

        membershipService.deductPoints(userId, 1000, PointsChangeReason.IAP_REVOKE, "refund");

        MembershipEntity m = membershipMapper.findByUserId(userId);
        assertThat(m.getCurrentPoints()).isZero();
    }

    @Test
    void downgradeFromSvipToVip() {
        Long userId = userService.register(new RegisterRequest("revoke2", "pw", null));
        membershipService.upgradeMembershipType(userId, MembershipType.SVIP);

        membershipService.downgradeMembershipType(userId, MembershipType.VIP);

        assertThat(membershipMapper.findByUserId(userId).getMembershipType()).isEqualTo(MembershipType.VIP);
    }

    @Test
    void revokeSubscriptionClearsMembership() {
        Long userId = userService.register(new RegisterRequest("revoke3", "pw", null));
        membershipService.purchaseSubscription(userId, SubscriptionType.MONTHLY);

        membershipService.revokeSubscription(userId);

        MembershipEntity m = membershipMapper.findByUserId(userId);
        assertThat(m.getHasMembership()).isFalse();
        assertThat(m.getSubscriptionExpireAt()).isNull();
    }
}
