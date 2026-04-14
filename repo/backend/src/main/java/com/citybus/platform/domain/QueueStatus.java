package com.citybus.platform.domain;

public enum QueueStatus {
    PENDING,
    PROCESSING,
    DELIVERED,
    FAILED_RETRYABLE,
    DEAD_LETTERED
}
