package com.lemon.music.musicbackservice.iap.service;

import com.lemon.music.musicbackservice.common.BusinessException;
import com.lemon.music.musicbackservice.iap.domain.IapOrderEntity;
import com.lemon.music.musicbackservice.iap.domain.OrderStatus;
import com.lemon.music.musicbackservice.iap.mapper.IapOrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IAP 订单取消功能服务层测试（{@link IapOrderService#cancelOrder}）。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>成功取消待支付订单（状态→CLOSED，记录 cancelledAt 与 cancelReason）</li>
 *   <li>订单不存在 → 抛 BusinessException</li>
 *   <li>订单状态非 PENDING → 抛 BusinessException（状态不允许取消）</li>
 *   <li>cancelReason 为 null → 落库默认值 "USER_CANCELLED"</li>
 * </ul>
 *
 * <p>使用 H2（MODE=MySQL）+ {@code @Sql} 准备测试数据，{@code @Transactional} 保证测试后回滚。
 * {@code @MockitoBean StringRedisTemplate} 用于满足 {@code AuthService} 的构造器依赖
 * （测试 profile 已排除 Redis 自动配置）。
 */
@SpringBootTest
@Transactional
class IapOrderServiceCancelTest {

    @MockitoBean
    @SuppressWarnings("unused")
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IapOrderService iapOrderService;

    @Autowired
    private IapOrderMapper iapOrderMapper;

    @Test
    @Sql(scripts = "/sql/test-cancel-order-setup.sql")
    void cancelOrderSuccessTransitionsToClosedAndPersistsReason() {
        boolean result = iapOrderService.cancelOrder("IAP_TEST_001", "用户主动取消");

        assertTrue(result);
        IapOrderEntity order = iapOrderMapper.findByOrderNo("IAP_TEST_001");
        assertEquals(OrderStatus.CLOSED, order.getStatus());
        assertNotNull(order.getCancelledAt(), "cancelledAt should be set after cancel");
        assertEquals("用户主动取消", order.getCancelReason());
    }

    @Test
    void cancelOrderNotFoundThrowsBusinessException() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> iapOrderService.cancelOrder("NON_EXISTENT_ORDER", null));

        // 实际消息为 "订单不存在: NON_EXISTENT_ORDER"
        assertTrue(exception.getMessage().contains("订单不存在"),
                "message should indicate order not found, was: " + exception.getMessage());
        assertTrue(exception.getMessage().contains("NON_EXISTENT_ORDER"),
                "message should include the order number, was: " + exception.getMessage());
    }

    @Test
    @Sql(scripts = "/sql/test-cancel-order-setup.sql")
    void cancelOrderNotPendingThrowsBusinessException() {
        // IAP_TEST_002 处于 PAID 状态，不允许取消
        BusinessException exception = assertThrows(BusinessException.class,
                () -> iapOrderService.cancelOrder("IAP_TEST_002", null));

        // 实际消息形如 "只有PENDING状态的订单可以取消，当前状态: PAID"
        assertTrue(exception.getMessage().contains("PENDING"),
                "message should mention PENDING requirement, was: " + exception.getMessage());
        assertTrue(exception.getMessage().contains("PAID"),
                "message should mention current status, was: " + exception.getMessage());

        // 订单状态不应被改动
        IapOrderEntity order = iapOrderMapper.findByOrderNo("IAP_TEST_002");
        assertEquals(OrderStatus.PAID, order.getStatus());
    }

    @Test
    @Sql(scripts = "/sql/test-cancel-order-setup.sql")
    void cancelOrderWithNullReasonDefaultsToUserCancelled() {
        boolean result = iapOrderService.cancelOrder("IAP_TEST_003", null);

        assertTrue(result);
        IapOrderEntity order = iapOrderMapper.findByOrderNo("IAP_TEST_003");
        assertEquals(OrderStatus.CLOSED, order.getStatus());
        assertEquals("USER_CANCELLED", order.getCancelReason());
        assertNotNull(order.getCancelledAt(), "cancelledAt should be set after cancel");
    }
}
