package com.lemon.music.musicbackservice.iap;

import com.lemon.music.musicbackservice.auth.AuthService;
import com.lemon.music.musicbackservice.auth.UserSession;
import com.lemon.music.musicbackservice.iap.domain.IapOrderEntity;
import com.lemon.music.musicbackservice.iap.domain.OrderStatus;
import com.lemon.music.musicbackservice.iap.mapper.IapOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IAP 订单取消功能端到端集成测试。
 *
 * <p>验证从 HTTP Controller → {@code AuthInterceptor}（鉴权 + 权限校验）→
 * {@code IapController}（归属校验）→ {@code IapOrderService}（业务规则）→
 * {@code IapOrderMapper} → H2 数据库的完整链路。
 *
 * <p>鉴权策略：测试 profile 已排除 Redis 自动配置，{@link AuthService} 依赖 Redis。
 * 为在不引入真实 Redis 的前提下完整跑通鉴权拦截器逻辑（Bearer token 解析、
 * {@code @RequirePermission} 注解校验、{@code AuthContext} 注入），将 {@link AuthService}
 * 以 {@code @MockitoBean} 替换，并在 {@code getSessionByToken(...)} 上返回受控的
 * {@link UserSession}。这样拦截器的真实代码仍被执行，仅"当前登录身份"由测试决定。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>成功取消自己的待支付订单（PENDING → CLOSED，cancelledAt/cancelReason 落库）</li>
 *   <li>非订单本人取消 → 业务失败（"无权限"），订单状态不变</li>
 *   <li>取消非 PENDING 订单 → 业务失败（状态校验），订单状态不变</li>
 *   <li>订单不存在 → 业务失败（"订单不存在"）</li>
 *   <li>无 Authorization 头 → 拦截器返回 401（鉴权层拦截）</li>
 * </ul>
 *
 * <p>数据准备复用 Task 7 的 {@code /sql/test-cancel-order-setup.sql}
 * （订单 IAP_TEST_001 PENDING / IAP_TEST_002 PAID / IAP_TEST_003 PENDING，
 * 均属于 user_id = 1001）。{@code @Transactional} 保证每个测试方法后回滚。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IapCancellationEndToEndTest {

    /** 与 test-cancel-order-setup.sql 中订单的 user_id 保持一致。 */
    private static final long OWNER_USER_ID = 1001L;
    private static final long OTHER_USER_ID = 2002L;
    private static final String OWNER_TOKEN = "test-token-owner";
    private static final String OTHER_TOKEN = "test-token-other";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Autowired
    private IapOrderMapper iapOrderMapper;

    @BeforeEach
    void setUpAuthSessions() {
        // 两个会话都持有 USER_SELF 权限（cancel 接口标注 @RequirePermission("USER_SELF")），
        // 这样拦截器层的权限校验对二者均放行，从而可以聚焦测试 controller 层的订单归属校验。
        UserSession owner = new UserSession(OWNER_USER_ID, "owner",
                Instant.now().getEpochSecond(), Set.of("USER_SELF"));
        UserSession other = new UserSession(OTHER_USER_ID, "other",
                Instant.now().getEpochSecond(), Set.of("USER_SELF"));
        when(authService.getSessionByToken(OWNER_TOKEN)).thenReturn(owner);
        when(authService.getSessionByToken(OTHER_TOKEN)).thenReturn(other);
    }

    /** 成功路径：本人取消自己的 PENDING 订单，验证完整状态转换与持久化。 */
    @Test
    @Sql(scripts = "/sql/test-cancel-order-setup.sql")
    void cancelOrder_EndToEnd_Success() throws Exception {
        mockMvc.perform(put("/api/iap/orders/IAP_TEST_001/cancel")
                .header("Authorization", "Bearer " + OWNER_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cancelReason\":\"用户取消\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("订单已取消"))
            .andExpect(jsonPath("$.data").value(true));

        // 完整链路落库验证
        IapOrderEntity order = iapOrderMapper.findByOrderNo("IAP_TEST_001");
        assertEquals(OrderStatus.CLOSED, order.getStatus(),
                "status should transition PENDING -> CLOSED");
        assertEquals("用户取消", order.getCancelReason());
        assertNotNull(order.getCancelledAt(), "cancelledAt should be persisted");
    }

    /** 权限验证：其他用户（同样持有 USER_SELF）取消他人订单应被 controller 归属校验拒绝。 */
    @Test
    @Sql(scripts = "/sql/test-cancel-order-setup.sql")
    void cancelOrder_EndToEnd_UnauthorizedUser() throws Exception {
        mockMvc.perform(put("/api/iap/orders/IAP_TEST_001/cancel")
                .header("Authorization", "Bearer " + OTHER_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cancelReason\":\"尝试取消他人订单\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(containsString("无权限")))
            .andExpect(jsonPath("$.data").value(nullValue()));

        // 归属校验失败不应产生任何副作用
        IapOrderEntity order = iapOrderMapper.findByOrderNo("IAP_TEST_001");
        assertEquals(OrderStatus.PENDING, order.getStatus(),
                "order status must not change when ownership check fails");
    }

    /** 业务规则：取消非 PENDING 订单（IAP_TEST_002 为 PAID）应失败。 */
    @Test
    @Sql(scripts = "/sql/test-cancel-order-setup.sql")
    void cancelOrder_EndToEnd_NonPendingStatusRejected() throws Exception {
        mockMvc.perform(put("/api/iap/orders/IAP_TEST_002/cancel")
                .header("Authorization", "Bearer " + OWNER_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cancelReason\":\"延迟取消\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(containsString("PENDING")))
            .andExpect(jsonPath("$.data").value(nullValue()));

        IapOrderEntity order = iapOrderMapper.findByOrderNo("IAP_TEST_002");
        assertEquals(OrderStatus.PAID, order.getStatus(),
                "PAID order must remain PAID after rejected cancel");
    }

    /** 业务规则：取消不存在的订单应失败。 */
    @Test
    @Sql(scripts = "/sql/test-cancel-order-setup.sql")
    void cancelOrder_EndToEnd_OrderNotFound() throws Exception {
        mockMvc.perform(put("/api/iap/orders/IAP_NON_EXISTENT/cancel")
                .header("Authorization", "Bearer " + OWNER_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cancelReason\":\"whatever\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(containsString("订单不存在")))
            .andExpect(jsonPath("$.data").value(nullValue()));
    }

    /** 鉴权层：缺少 Authorization 头时由拦截器直接返回 401。 */
    @Test
    @Sql(scripts = "/sql/test-cancel-order-setup.sql")
    void cancelOrder_EndToEnd_NoAuthHeader_Unauthorized() throws Exception {
        var result = mockMvc.perform(put("/api/iap/orders/IAP_TEST_001/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cancelReason\":\"无凭证\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andReturn();

        // Verify the response matches auth error format
        String response = result.getResponse().getContentAsString();
        assertNotNull(response);
        assertTrue(response.contains("\"success\":false") && (response.contains("unauthorized") || response.contains("Unauthorized") || response.contains("Authentication") || response.contains("鉴权")),
                "401 response should indicate authentication failure");

        // 未鉴权自然不应改动订单
        IapOrderEntity order = iapOrderMapper.findByOrderNo("IAP_TEST_001");
        assertEquals(OrderStatus.PENDING, order.getStatus());
    }
}
