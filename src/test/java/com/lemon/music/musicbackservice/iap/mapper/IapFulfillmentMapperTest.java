package com.lemon.music.musicbackservice.iap.mapper;

import com.lemon.music.musicbackservice.iap.domain.FulfillmentAction;
import com.lemon.music.musicbackservice.iap.domain.IapFulfillmentEntity;
import com.lemon.music.musicbackservice.iap.domain.IapProductType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class IapFulfillmentMapperTest {

    @MockitoBean
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IapFulfillmentMapper mapper;

    @Test
    void insertAndFindByPoAndAction() {
        grant("PO1", 100);

        IapFulfillmentEntity found = mapper.findByPurchaseOrderIdAndAction("PO1", FulfillmentAction.GRANT);
        assertThat(found).isNotNull();
        assertThat(found.getPointsGranted()).isEqualTo(100);
        assertThat(found.getIapProductType()).isEqualTo(IapProductType.CONSUMABLE);
    }

    @Test
    void uniqueConstraintPreventsDuplicateGrant() {
        grant("PO2", 50);
        // 同 (poId, GRANT) 再次插入应触发唯一约束
        assertThatThrownBy(() -> grant("PO2", 50)).isInstanceOf(Exception.class);
    }

    private IapFulfillmentEntity grant(String poId, int points) {
        IapFulfillmentEntity f = new IapFulfillmentEntity();
        f.setHuaweiPurchaseOrderId(poId);
        f.setHuaweiPurchaseToken("T_" + poId);
        f.setUserId(1L);
        f.setIapProductId(1L);
        f.setIapProductType(IapProductType.CONSUMABLE);
        f.setAction(FulfillmentAction.GRANT);
        f.setPointsGranted(points);
        f.setEffectSnapshot("{}");
        f.setCreatedAt(LocalDateTime.now());
        mapper.insert(f);
        return f;
    }
}
