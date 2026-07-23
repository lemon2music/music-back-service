package com.lemon.music.musicbackservice.membership.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 会员系统定时任务。
 *
 * <p>每天凌晨 1 点处理所有会员的每日积分变化（增长/扣减/订阅到期）。
 * 需要在配置类上启用 {@code @EnableScheduling}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MembershipScheduler {

    private final MembershipService membershipService;

    /**
     * 每天凌晨 1 点执行。
     * cron: 秒 分 时 日 月 周
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void processDailyPointsChanges() {
        log.info("定时任务触发：开始处理每日会员积分变化");
        try {
            membershipService.processDailyPointsChange();
        } catch (Exception e) {
            log.error("定时任务处理每日会员积分变化失败", e);
        }
    }
}
