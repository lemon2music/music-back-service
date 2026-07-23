package com.lemon.music.musicbackservice.membership.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分变化历史（基础审计记录）。
 */
@Data
public class MembershipPointsHistoryEntity {

    private Long id;
    private Long userId;
    /** 变化量，正数为增加，负数为扣减 */
    private Integer pointsChange;
    private Integer pointsBefore;
    private Integer pointsAfter;
    private PointsChangeReason changeReason;
    private String description;
    private LocalDateTime createdAt;
}
