package com.lemon.music.musicbackservice.membership.dto.response;

import com.lemon.music.musicbackservice.membership.domain.PointsChangeReason;

import java.time.LocalDateTime;

public record PointsHistoryResponse(
        Long id,
        Integer pointsChange,
        Integer pointsBefore,
        Integer pointsAfter,
        PointsChangeReason reason,
        String description,
        LocalDateTime createdAt
) {
}
