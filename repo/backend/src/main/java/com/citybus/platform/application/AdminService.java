package com.citybus.platform.application;

import com.citybus.platform.api.dto.AdminDtos;
import com.citybus.platform.domain.FieldDictionary;
import com.citybus.platform.domain.NotificationTemplate;
import com.citybus.platform.domain.ParsedRecord;
import com.citybus.platform.domain.ParsedRecordStatus;
import com.citybus.platform.domain.ParsingTemplate;
import com.citybus.platform.domain.PasswordResetToken;
import com.citybus.platform.domain.SearchWeightConfig;
import com.citybus.platform.domain.SystemAlert;
import com.citybus.platform.domain.User;
import com.citybus.platform.domain.CleaningRule;
import com.citybus.platform.infrastructure.persistence.SystemAlertRepository;
import com.citybus.platform.infrastructure.persistence.CleaningRuleRepository;
import com.citybus.platform.infrastructure.persistence.FieldDictionaryRepository;
import com.citybus.platform.infrastructure.persistence.NotificationTemplateRepository;
import com.citybus.platform.infrastructure.persistence.ParsedRecordRepository;
import com.citybus.platform.infrastructure.persistence.ParsingTemplateRepository;
import com.citybus.platform.infrastructure.persistence.PasswordResetTokenRepository;
import com.citybus.platform.infrastructure.persistence.SearchWeightConfigRepository;
import com.citybus.platform.infrastructure.persistence.SessionRepository;
import com.citybus.platform.infrastructure.persistence.UserRepository;
import com.citybus.platform.infrastructure.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminService {
    private final NotificationTemplateRepository notificationTemplateRepository;
    private final SearchWeightConfigRepository searchWeightConfigRepository;
    private final FieldDictionaryRepository fieldDictionaryRepository;
    private final CleaningRuleRepository cleaningRuleRepository;
    private final ParsingTemplateRepository parsingTemplateRepository;
    private final ParsedRecordRepository parsedRecordRepository;
    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final SystemAlertRepository systemAlertRepository;
    private final DiagnosticReportService diagnosticReportService;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    public AdminService(
            NotificationTemplateRepository notificationTemplateRepository,
            SearchWeightConfigRepository searchWeightConfigRepository,
            FieldDictionaryRepository fieldDictionaryRepository,
            CleaningRuleRepository cleaningRuleRepository,
            ParsingTemplateRepository parsingTemplateRepository,
            ParsedRecordRepository parsedRecordRepository,
            UserRepository userRepository,
            SessionRepository sessionRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            SystemAlertRepository systemAlertRepository,
            DiagnosticReportService diagnosticReportService,
            AuditService auditService,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper
    ) {
        this.notificationTemplateRepository = notificationTemplateRepository;
        this.searchWeightConfigRepository = searchWeightConfigRepository;
        this.fieldDictionaryRepository = fieldDictionaryRepository;
        this.cleaningRuleRepository = cleaningRuleRepository;
        this.parsingTemplateRepository = parsingTemplateRepository;
        this.parsedRecordRepository = parsedRecordRepository;
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.systemAlertRepository = systemAlertRepository;
        this.diagnosticReportService = diagnosticReportService;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.TemplateResponse> templates() {
        return notificationTemplateRepository.findAll().stream()
                .map(item -> new AdminDtos.TemplateResponse(item.getId().toString(), item.getTemplateKey(), item.getTitleTemplate(), item.getContentTemplate(), item.getUpdatedAt()))
                .toList();
    }

    @Transactional
    public AdminDtos.TemplateResponse updateTemplate(AuthenticatedUser currentUser, UUID id, AdminDtos.TemplateUpdateRequest request) {
        NotificationTemplate template = notificationTemplateRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Template not found"));
        template.setTitleTemplate(request.titleTemplate());
        template.setContentTemplate(request.contentTemplate());
        template.setUpdatedAt(Instant.now());
        notificationTemplateRepository.save(template);
        auditService.log(currentUser.userId(), "TEMPLATE_UPDATED", "TEMPLATE", id.toString(), template.getTemplateKey());
        return new AdminDtos.TemplateResponse(template.getId().toString(), template.getTemplateKey(), template.getTitleTemplate(), template.getContentTemplate(), template.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public AdminDtos.SearchConfigResponse searchConfig() {
        SearchWeightConfig config = searchWeightConfigRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Search config not found"));
        return new AdminDtos.SearchConfigResponse(config.getId().toString(), config.getExactWeight(), config.getPrefixWeight(), config.getPinyinWeight(), config.getPopularityWeight(), config.getFrequencyWeight(), config.getRevision());
    }

    @Transactional
    public AdminDtos.SearchConfigResponse updateSearchConfig(AuthenticatedUser currentUser, AdminDtos.SearchConfigUpdateRequest request) {
        SearchWeightConfig config = searchWeightConfigRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Search config not found"));
        if (config.getRevision() != request.revision()) {
            throw new ApiException(HttpStatus.CONFLICT, "Search config revision conflict");
        }
        config.setExactWeight(request.exactWeight());
        config.setPrefixWeight(request.prefixWeight());
        config.setPinyinWeight(request.pinyinWeight());
        config.setPopularityWeight(request.popularityWeight());
        config.setFrequencyWeight(request.frequencyWeight());
        config.setRevision(config.getRevision() + 1);
        config.setUpdatedAt(Instant.now());
        searchWeightConfigRepository.save(config);
        auditService.log(currentUser.userId(), "SEARCH_CONFIG_UPDATED", "SEARCH_CONFIG", config.getId().toString(), "Weights updated");
        return searchConfig();
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.DictionaryGroupResponse> dictionaries() {
        Map<String, List<AdminDtos.DictionaryResponse>> grouped = fieldDictionaryRepository.findAll().stream()
                .map(item -> new AdminDtos.DictionaryResponse(item.getId().toString(), item.getDictionaryType(), item.getSourceValue(), item.getStandardizedValue(), item.getUpdatedAt()))
                .collect(Collectors.groupingBy(AdminDtos.DictionaryResponse::dictionaryType));
        return grouped.entrySet().stream()
                .map(entry -> new AdminDtos.DictionaryGroupResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.CleaningRuleResponse> cleaningRules() {
        return cleaningRuleRepository.findAll().stream()
                .map(rule -> new AdminDtos.CleaningRuleResponse(rule.getId().toString(), rule.getRuleKey(), rule.getFieldName(), rule.getPattern(), rule.getReplacementValue(), rule.isEnabled(), rule.getUpdatedAt()))
                .toList();
    }

    @Transactional
    public AdminDtos.CleaningRuleResponse updateCleaningRule(AuthenticatedUser currentUser, UUID id, AdminDtos.CleaningRuleUpdateRequest request) {
        CleaningRule rule = cleaningRuleRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Cleaning rule not found"));
        rule.setPattern(request.pattern());
        rule.setReplacementValue(request.replacementValue());
        rule.setEnabled(request.enabled());
        rule.setUpdatedAt(Instant.now());
        cleaningRuleRepository.save(rule);
        auditService.log(currentUser.userId(), "CLEANING_RULE_UPDATED", "CLEANING_RULE", id.toString(), rule.getRuleKey());
        return new AdminDtos.CleaningRuleResponse(rule.getId().toString(), rule.getRuleKey(), rule.getFieldName(), rule.getPattern(), rule.getReplacementValue(), rule.isEnabled(), rule.getUpdatedAt());
    }

    @Transactional
    public AdminDtos.DictionaryResponse updateDictionary(AuthenticatedUser currentUser, UUID id, AdminDtos.DictionaryUpdateRequest request) {
        FieldDictionary dictionary = fieldDictionaryRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Dictionary record not found"));
        dictionary.setStandardizedValue(request.standardizedValue());
        dictionary.setUpdatedAt(Instant.now());
        fieldDictionaryRepository.save(dictionary);
        auditService.log(currentUser.userId(), "DICTIONARY_UPDATED", "FIELD_DICTIONARY", id.toString(), dictionary.getSourceValue());
        return new AdminDtos.DictionaryResponse(dictionary.getId().toString(), dictionary.getDictionaryType(), dictionary.getSourceValue(), dictionary.getStandardizedValue(), dictionary.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.ParsingTemplateResponse> parsingTemplates() {
        return parsingTemplateRepository.findAll().stream()
                .map(item -> new AdminDtos.ParsingTemplateResponse(item.getId().toString(), item.getTemplateName(), item.getTemplateType(), item.getSemanticVersion(), item.getRevision(), item.isActive(), item.getContentHash(), item.getUpdatedAt()))
                .toList();
    }

    @Transactional
    public AdminDtos.ParsingTemplateResponse saveParsingTemplate(AuthenticatedUser currentUser, AdminDtos.ParsingTemplateRequest request) {
        validateTemplateBody(request.templateType(), request.body());
        String incomingHash = sha256(request.body());
        ParsingTemplate latest = parsingTemplateRepository.findByTemplateNameIgnoreCaseOrderByRevisionDesc(request.templateName())
                .stream()
                .findFirst()
                .orElse(null);

        int nextRevision = 1;
        if (latest != null) {
            if (latest.getRevision() != request.revision()) {
                if (!incomingHash.equals(latest.getContentHash())) {
                    throw new ApiException(HttpStatus.CONFLICT, "Template revision conflict. Manual merge required");
                }
                throw new ApiException(HttpStatus.CONFLICT, "Template revision conflict");
            }
            nextRevision = latest.getRevision() + 1;
        } else if (request.revision() != 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Template revision conflict");
        }

        ParsingTemplate template = new ParsingTemplate();
        template.setId(UUID.randomUUID());
        template.setTemplateName(request.templateName());
        template.setTemplateType(request.templateType());
        template.setSemanticVersion(request.semanticVersion());
        template.setBody(request.body());
        template.setActive(request.active());
        template.setContentHash(incomingHash);
        template.setRevision(nextRevision);
        template.setUpdatedAt(Instant.now());
        if (template.isActive()) {
            parsingTemplateRepository.findByTemplateTypeOrderByUpdatedAtDesc(template.getTemplateType()).stream()
                    .filter(ParsingTemplate::isActive)
                    .forEach(item -> {
                        item.setActive(false);
                        item.setUpdatedAt(Instant.now());
                        parsingTemplateRepository.save(item);
                    });
        }
        parsingTemplateRepository.save(template);
        auditService.log(currentUser.userId(), "PARSING_TEMPLATE_SAVED", "PARSING_TEMPLATE", template.getId().toString(), template.getTemplateName());
        return new AdminDtos.ParsingTemplateResponse(template.getId().toString(), template.getTemplateName(), template.getTemplateType(), template.getSemanticVersion(), template.getRevision(), template.isActive(), template.getContentHash(), template.getUpdatedAt());
    }

    @Transactional
    public AdminDtos.ParsedRecordResponse parseSource(AuthenticatedUser currentUser, AdminDtos.ParseRequest request) {
        ParsingTemplate template = parsingTemplateRepository.findById(parseUuid(request.templateId(), "Parsing template"))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Parsing template not found"));

        Map<String, String> extracted = extractMappedFields(template.getTemplateType(), template.getBody(), request.sourceBody());
        Map<String, String> normalized = new HashMap<>();
        normalized.put("stopName", nullIfBlank(extracted.get("stopName")));
        normalized.put("address", nullIfBlank(extracted.get("address")));
        normalized.put("residentialAreaName", nullIfBlank(extracted.get("residentialAreaName")));
        normalized.put("apartmentType", nullIfBlank(extracted.get("apartmentType")));
        normalized.put("area", normalizeArea(applyCleaningRules("area", extracted.get("area"))));
        normalized.put("price", normalizePrice(applyCleaningRules("price", extracted.get("price"))));
        normalized.put("stopName", applyCleaningRules("stopName", normalized.get("stopName")));
        normalized.put("address", applyCleaningRules("address", normalized.get("address")));
        normalized.put("residentialAreaName", applyCleaningRules("residentialAreaName", normalized.get("residentialAreaName")));
        normalized.put("apartmentType", applyCleaningRules("apartmentType", normalized.get("apartmentType")));

        ParsedRecord record = new ParsedRecord();
        record.setId(UUID.randomUUID());
        record.setTemplateId(template.getId());
        record.setSourceReference(request.sourceReference());
        record.setNormalizedPayload(toJson(normalized));
        record.setTemplateSemanticVersion(template.getSemanticVersion());
        record.setTemplateContentHash(template.getContentHash());
        record.setCleaningRuleSnapshot(cleaningRuleSnapshot());
        record.setSourceLog(request.sourceBody());
        if (normalized.values().stream().anyMatch(value -> value == null)) {
            record.setStatus(ParsedRecordStatus.WARNING);
            record.setWarningMessage("One or more fields were missing and stored as NULL");
        } else {
            record.setStatus(ParsedRecordStatus.SUCCESS);
        }
        record.setCreatedAt(Instant.now());
        parsedRecordRepository.save(record);
        auditService.log(currentUser.userId(), "PARSE_EXECUTED", "PARSED_RECORD", record.getId().toString(), request.sourceReference());
        return new AdminDtos.ParsedRecordResponse(record.getId().toString(), record.getStatus().name(), record.getSourceReference(), record.getNormalizedPayload(), record.getWarningMessage(), record.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.AlertResponse> alerts() {
        return systemAlertRepository.findTop20ByOrderByCreatedAtDesc().stream()
                .map(alert -> new AdminDtos.AlertResponse(alert.getId().toString(), alert.getAlertType(), alert.getSeverity(), alert.getSummary(), alert.getDetails(), alert.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.DiagnosticReportResponse> reports() {
        return diagnosticReportService.latestReports();
    }

    @Transactional(readOnly = true)
    public AdminDtos.DiagnosticReportResponse report(UUID id) {
        return diagnosticReportService.report(id);
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.UserSummary> users() {
        return userRepository.findAll().stream()
                .map(user -> new AdminDtos.UserSummary(user.getId().toString(), user.getUsername(), user.getDisplayName(), user.getRoleName().name(), user.isActive(), user.isTemporaryPassword()))
                .toList();
    }

    @Transactional
    public AdminDtos.PasswordResetIssueResponse resetUserPassword(AuthenticatedUser currentUser, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        String temporaryPassword = "Admin" + UUID.randomUUID().toString().substring(0, 8) + "!";
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setTemporaryPassword(true);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        sessionRepository.findByUserIdAndRevokedFalse(user.getId()).forEach(session -> {
            session.setRevoked(true);
            sessionRepository.save(session);
        });
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setId(UUID.randomUUID());
        resetToken.setUserId(user.getId());
        resetToken.setIssuedByUserId(currentUser.userId());
        resetToken.setRequestToken("RST-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase());
        resetToken.setTemporaryPasswordPlain(temporaryPassword);
        resetToken.setExpiresAt(Instant.now().plusSeconds(300));
        resetToken.setConsumed(false);
        resetToken.setCreatedAt(Instant.now());
        passwordResetTokenRepository.save(resetToken);
        auditService.log(currentUser.userId(), "ADMIN_PASSWORD_RESET_ISSUED", "USER", user.getId().toString(), username);
        return new AdminDtos.PasswordResetIssueResponse(resetToken.getRequestToken(), resetToken.getExpiresAt());
    }

    @Transactional
    public String consumePasswordResetToken(AuthenticatedUser currentUser, String requestToken) {
        PasswordResetToken token = passwordResetTokenRepository.findByRequestToken(requestToken)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Password reset token not found"));
        if (token.isConsumed() || token.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "Password reset token expired or consumed");
        }
        token.setConsumed(true);
        token.setConsumedAt(Instant.now());
        passwordResetTokenRepository.save(token);
        auditService.log(currentUser.userId(), "ADMIN_PASSWORD_RESET_REVEALED", "USER", token.getUserId().toString(), requestToken);
        return token.getTemporaryPasswordPlain();
    }

    private void validateTemplateBody(String templateType, String body) {
        try {
            objectMapper.readTree(body);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Template body must be valid JSON mapping metadata");
        }
        if (!List.of("JSON", "HTML").contains(templateType.toUpperCase())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Unsupported template type");
        }
    }

    private Map<String, String> extractMappedFields(String templateType, String mappingBody, String sourceBody) {
        try {
            JsonNode root = objectMapper.readTree(mappingBody);
            JsonNode fields = root.get("fields");
            if (fields == null || !fields.isObject()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Template must define a fields object");
            }
            Map<String, String> values = new HashMap<>();
            if ("JSON".equalsIgnoreCase(templateType)) {
                JsonNode source = objectMapper.readTree(sourceBody);
                fields.fields().forEachRemaining(entry -> values.put(entry.getKey(), extractJsonPath(source, entry.getValue().asText())));
            } else {
                Document document = Jsoup.parse(sourceBody);
                fields.fields().forEachRemaining(entry -> values.put(entry.getKey(), extractHtmlValue(document, entry.getValue())));
            }
            return values;
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Unable to parse source with template");
        }
    }

    private String extractJsonPath(JsonNode source, String path) {
        JsonNode current = source;
        for (String token : path.split("\\.")) {
            if (current == null) {
                return null;
            }
            current = current.get(token);
        }
        return current == null || current.isNull() ? null : current.asText();
    }

    private String extractHtmlValue(Document document, JsonNode config) {
        String selector = config.path("selector").asText(null);
        if (selector == null) {
            return null;
        }
        Element element = document.selectFirst(selector);
        if (element == null) {
            return null;
        }
        String attribute = config.path("attribute").asText("text");
        return "text".equalsIgnoreCase(attribute) ? element.text() : element.attr(attribute);
    }

    private String normalizePrice(String price) {
        return price == null || price.isBlank() ? null : price.replaceAll("[^\\d.]", "") + " yuan/month";
    }

    private String normalizeArea(String area) {
        return area == null || area.isBlank() ? null : area.replaceAll("[^\\d.]", "") + " \u33A1";
    }

    private String applyCleaningRules(String fieldName, String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value;
        for (CleaningRule rule : cleaningRuleRepository.findByEnabledTrueOrderByFieldNameAscRuleKeyAsc()) {
            if (rule.getFieldName().equalsIgnoreCase(fieldName)) {
                cleaned = cleaned.replaceAll(rule.getPattern(), rule.getReplacementValue());
            }
        }
        return nullIfBlank(cleaned);
    }

    private String nullIfBlank(String input) {
        return input == null || input.isBlank() ? null : input.trim();
    }

    private String toJson(Map<String, String> normalized) {
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String cleaningRuleSnapshot() {
        try {
            return objectMapper.writeValueAsString(
                    cleaningRuleRepository.findByEnabledTrueOrderByFieldNameAscRuleKeyAsc().stream()
                            .map(rule -> Map.of(
                                    "ruleKey", rule.getRuleKey(),
                                    "fieldName", rule.getFieldName(),
                                    "pattern", rule.getPattern(),
                                    "replacementValue", rule.getReplacementValue()
                            ))
                            .toList()
            );
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private UUID parseUuid(String rawValue, String label) {
        try {
            return UUID.fromString(rawValue);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, label + " identifier is invalid");
        }
    }
}
