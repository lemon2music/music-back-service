package com.lemon.music.musicbackservice.iap.dto.payload;

import java.util.List;

/** jwsSubscriptionStatus 验签解码后的订阅组状态载荷。 */
public record SubGroupStatusPayload(
        String environment,
        String applicationId,
        String packageName,
        String subGroupId,
        SubscriptionStatus lastSubscriptionStatus,
        List<SubscriptionStatus> historySubscriptionStatusList
) {}
