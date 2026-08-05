package com.mgmtp.gives.service.support;

public final class CampaignResultMetrics {

    private CampaignResultMetrics() {
    }

    /**
     * Intentionally uncapped: reports and AI prompts must preserve overfunding. Only fixed-width
     * visual controls should clamp this value when they render it.
     */
    public static double calculateGoalPercent(Long target, long amount) {
        return target != null && target > 0 ? (amount * 100.0) / target : 0.0;
    }
}
