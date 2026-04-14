package com.citybus.platform.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public final class AdminDtos {
    private AdminDtos() {
    }

    public record TemplateResponse(String id, String templateKey, String titleTemplate, String contentTemplate, Instant updatedAt) {}
    public record TemplateUpdateRequest(@NotBlank String titleTemplate, @NotBlank String contentTemplate) {}

    public record SearchConfigResponse(String id, int exactWeight, int prefixWeight, int pinyinWeight, int popularityWeight, int frequencyWeight, int revision) {}
    public record SearchConfigUpdateRequest(@Min(0) int exactWeight, @Min(0) int prefixWeight, @Min(0) int pinyinWeight, @Min(0) int popularityWeight, @Min(0) int frequencyWeight, @Min(0) int revision) {}

    public record DictionaryResponse(String id, String dictionaryType, String sourceValue, String standardizedValue, Instant updatedAt) {}
    public record DictionaryUpdateRequest(@NotBlank String standardizedValue) {}
    public record CleaningRuleResponse(String id, String ruleKey, String fieldName, String pattern, String replacementValue, boolean enabled, Instant updatedAt) {}
    public record CleaningRuleUpdateRequest(@NotBlank String pattern, @NotNull String replacementValue, boolean enabled) {}

    public record ParsingTemplateResponse(String id, String templateName, String templateType, String semanticVersion, int revision, boolean active, String contentHash, Instant updatedAt) {}
    public record ParsingTemplateRequest(@NotBlank String templateName, @NotBlank String templateType, @NotBlank String semanticVersion, @NotBlank String body, boolean active, int revision) {}

    public record ParsedRecordResponse(String id, String status, String sourceReference, String normalizedPayload, String warningMessage, Instant createdAt) {}
    public record ParseRequest(@NotBlank String templateId, @NotBlank String sourceReference, @NotBlank String sourceBody) {}
    public record DictionaryGroupResponse(String dictionaryType, List<DictionaryResponse> items) {}
    public record UserSummary(String id, String username, String displayName, String role, boolean active, boolean temporaryPassword) {}
    public record ResetPasswordRequest(@NotBlank String username) {}
    public record PasswordResetTokenRequest(@NotBlank String requestToken) {}
    public record PasswordResetIssueResponse(String requestToken, Instant expiresAt) {}
    public record AlertResponse(String id, String alertType, String severity, String summary, String details, Instant createdAt) {}
    public record DiagnosticReportResponse(String id, String reportType, String summaryJson, String traceId, Instant generatedAt) {}
}
