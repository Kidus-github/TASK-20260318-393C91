package com.citybus.platform.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String version,
        Security security,
        Reminders reminders,
        Queue queue,
        Search search,
        Alerts alerts
) {
    public record Security(int accessTokenMinutes, int refreshTokenDays, String accessSecret, String refreshSecret) {}
    public record Reminders(int defaultLeadMinutes, int catchupWindowMinutes) {}
    public record Queue(int maxRetries) {}
    public record Search(int exactWeight, int prefixWeight, int pinyinWeight, int popularityWeight, int frequencyWeight) {}
    public record Alerts(int queueBacklogThreshold, long apiP95ThresholdMs) {}
}
