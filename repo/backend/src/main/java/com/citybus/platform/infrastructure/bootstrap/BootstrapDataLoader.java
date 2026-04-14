package com.citybus.platform.infrastructure.bootstrap;

import com.citybus.platform.domain.FieldDictionary;
import com.citybus.platform.domain.NotificationTemplate;
import com.citybus.platform.domain.ParsingTemplate;
import com.citybus.platform.domain.RecoveryCode;
import com.citybus.platform.domain.RoleName;
import com.citybus.platform.domain.Route;
import com.citybus.platform.domain.SearchWeightConfig;
import com.citybus.platform.domain.Stop;
import com.citybus.platform.domain.User;
import com.citybus.platform.domain.WorkflowRule;
import com.citybus.platform.domain.WorkflowState;
import com.citybus.platform.domain.WorkflowTask;
import com.citybus.platform.domain.CleaningRule;
import com.citybus.platform.infrastructure.persistence.CleaningRuleRepository;
import com.citybus.platform.infrastructure.persistence.FieldDictionaryRepository;
import com.citybus.platform.infrastructure.persistence.NotificationTemplateRepository;
import com.citybus.platform.infrastructure.persistence.ParsingTemplateRepository;
import com.citybus.platform.infrastructure.persistence.RecoveryCodeRepository;
import com.citybus.platform.infrastructure.persistence.RouteRepository;
import com.citybus.platform.infrastructure.persistence.RouteStopRepository;
import com.citybus.platform.infrastructure.persistence.SearchWeightConfigRepository;
import com.citybus.platform.infrastructure.persistence.StopRepository;
import com.citybus.platform.infrastructure.persistence.UserRepository;
import com.citybus.platform.infrastructure.persistence.WorkflowTaskRepository;
import com.citybus.platform.infrastructure.persistence.WorkflowRuleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Component
public class BootstrapDataLoader implements CommandLineRunner {
    private final UserRepository userRepository;
    private final RouteRepository routeRepository;
    private final StopRepository stopRepository;
    private final NotificationTemplateRepository notificationTemplateRepository;
    private final SearchWeightConfigRepository searchWeightConfigRepository;
    private final FieldDictionaryRepository fieldDictionaryRepository;
    private final CleaningRuleRepository cleaningRuleRepository;
    private final ParsingTemplateRepository parsingTemplateRepository;
    private final RecoveryCodeRepository recoveryCodeRepository;
    private final RouteStopRepository routeStopRepository;
    private final WorkflowTaskRepository workflowTaskRepository;
    private final WorkflowRuleRepository workflowRuleRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;
    private final JdbcTemplate jdbcTemplate;

    public BootstrapDataLoader(
            UserRepository userRepository,
            RouteRepository routeRepository,
            StopRepository stopRepository,
            NotificationTemplateRepository notificationTemplateRepository,
            SearchWeightConfigRepository searchWeightConfigRepository,
            FieldDictionaryRepository fieldDictionaryRepository,
            CleaningRuleRepository cleaningRuleRepository,
            ParsingTemplateRepository parsingTemplateRepository,
            RecoveryCodeRepository recoveryCodeRepository,
            RouteStopRepository routeStopRepository,
            WorkflowTaskRepository workflowTaskRepository,
            WorkflowRuleRepository workflowRuleRepository,
            PasswordEncoder passwordEncoder,
            Environment environment,
            JdbcTemplate jdbcTemplate
    ) {
        this.userRepository = userRepository;
        this.routeRepository = routeRepository;
        this.stopRepository = stopRepository;
        this.notificationTemplateRepository = notificationTemplateRepository;
        this.searchWeightConfigRepository = searchWeightConfigRepository;
        this.fieldDictionaryRepository = fieldDictionaryRepository;
        this.cleaningRuleRepository = cleaningRuleRepository;
        this.parsingTemplateRepository = parsingTemplateRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.routeStopRepository = routeStopRepository;
        this.workflowTaskRepository = workflowTaskRepository;
        this.workflowRuleRepository = workflowRuleRepository;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedUsers();
        seedSearchData();
        seedAdminData();
        seedWorkflowTasks();
        seedWorkflowRules();
    }

    private void seedUsers() {
        if (userRepository.count() > 0) {
            userRepository.findAll().forEach(this::seedRecoveryCode);
            return;
        }
        userRepository.saveAll(List.of(
                user("admin", seededPassword("admin"), "Operations Admin", RoleName.ADMIN),
                user("dispatcher", seededPassword("dispatcher"), "Central Dispatcher", RoleName.DISPATCHER),
                user("passenger", seededPassword("passenger"), "Passenger User", RoleName.PASSENGER)
        ));
        userRepository.findAll().forEach(this::seedRecoveryCode);
    }

    private void seedSearchData() {
        if (routeRepository.count() > 0) {
            ensureRouteStopLinks();
            return;
        }
        Route routeA = new Route();
        routeA.setId(UUID.randomUUID());
        routeA.setRouteNumber("101");
        routeA.setRouteName("Central Station - Riverside");
        routeA.setFrequencyPriority(5);
        routeA.setActive(true);

        Route routeB = new Route();
        routeB.setId(UUID.randomUUID());
        routeB.setRouteNumber("18A");
        routeB.setRouteName("Old Town Loop");
        routeB.setFrequencyPriority(3);
        routeB.setActive(true);

        Stop stopA = stop("Central Station", "zhongxinchezhan", "zxcz", "hub train terminal", "1 Main Street", 8);
        Stop stopB = stop("Riverside Park", "binjianggongyuan", "bjgy", "river park", "88 Riverside Ave", 7);
        Stop stopC = stop("Old Town North", "laochengbei", "lcb", "historic district", "12 Heritage Road", 4);
        routeRepository.saveAll(List.of(routeA, routeB));
        stopRepository.saveAll(List.of(stopA, stopB, stopC));
        ensureRouteStopLinks();
    }

    private void seedAdminData() {
        if (notificationTemplateRepository.count() == 0) {
            notificationTemplateRepository.saveAll(List.of(
                    template("REMINDER_PREF_UPDATED", "Reminder settings updated", "Your reminder settings have been updated successfully."),
                    template("RESERVATION_CONFIRMED", "Reservation confirmed", "Reminder reserved for {{routeNumber}} at {{stopName}}."),
                    template("UPCOMING_REMINDER", "Upcoming arrival reminder", "Upcoming reminder for {{reservationName}}."),
                    template("MISSED_CHECK_IN", "Missed check-in", "No check-in was recorded within 5 minutes of {{reservationName}}."),
                    template("REMINDER_CANCELLED", "Reminder cancelled", "The reminder reservation for {{reservationName}} was cancelled."),
                    template("CHECK_IN_SUCCESS", "Check-in recorded", "You checked in for {{reservationName}}.")
            ));
        }
        if (searchWeightConfigRepository.count() == 0) {
            SearchWeightConfig config = new SearchWeightConfig();
            config.setId(UUID.randomUUID());
            config.setExactWeight(10);
            config.setPrefixWeight(7);
            config.setPinyinWeight(5);
            config.setPopularityWeight(3);
            config.setFrequencyWeight(4);
            config.setRevision(1);
            config.setUpdatedAt(Instant.now());
            searchWeightConfigRepository.save(config);
        }
        if (fieldDictionaryRepository.count() == 0) {
            FieldDictionary area = new FieldDictionary();
            area.setId(UUID.randomUUID());
            area.setDictionaryType("AREA_UNIT");
            area.setSourceValue("sqm");
            area.setStandardizedValue("\u33A1");
            area.setUpdatedAt(Instant.now());
            FieldDictionary price = new FieldDictionary();
            price.setId(UUID.randomUUID());
            price.setDictionaryType("PRICE_UNIT");
            price.setSourceValue("CNY/month");
            price.setStandardizedValue("yuan/month");
            price.setUpdatedAt(Instant.now());
            fieldDictionaryRepository.saveAll(List.of(area, price));
        }
        if (cleaningRuleRepository.count() == 0) {
            cleaningRuleRepository.saveAll(List.of(
                    cleaningRule("PRICE_STRIP_NON_DIGITS", "price", "[^\\d.]", ""),
                    cleaningRule("AREA_STRIP_NON_DIGITS", "area", "[^\\d.]", ""),
                    cleaningRule("STOP_NAME_TRIM", "stopName", "\\s{2,}", " "),
                    cleaningRule("ADDRESS_TRIM", "address", "\\s{2,}", " ")
            ));
        }
        if (parsingTemplateRepository.count() == 0) {
            String body = "{\"fields\":{\"stopName\":\"stop.name\",\"address\":\"stop.address\",\"residentialAreaName\":\"housing.areaName\",\"apartmentType\":\"housing.apartmentType\",\"area\":\"housing.area\",\"price\":\"housing.price\"}}";
            ParsingTemplate template = new ParsingTemplate();
            template.setId(UUID.randomUUID());
            template.setTemplateName("default-json-template");
            template.setTemplateType("JSON");
            template.setSemanticVersion("1.0.0");
            template.setRevision(1);
            template.setContentHash(sha256(body));
            template.setActive(true);
            template.setBody(body);
            template.setUpdatedAt(Instant.now());
            parsingTemplateRepository.save(template);
        }
    }

    private void seedWorkflowTasks() {
        if (workflowTaskRepository.count() > 0) {
            return;
        }
        workflowTaskRepository.saveAll(List.of(
                workflowTask(
                        "Route data change approval",
                        "ROUTE_CHANGE",
                        "ALL",
                        2,
                        "routeNumber=101;change=Stop address updated;requiresAbnormalReview=true"
                ),
                workflowTask(
                        "Reminder rule configuration review",
                        "REMINDER_RULE_CONFIG",
                        "ALL",
                        2,
                        "ruleKey=LEAD_MINUTES;requestedValue=15;requiresAbnormalReview=false"
                ),
                workflowTask(
                        "Abnormal data review queue",
                        "ABNORMAL_DATA_REVIEW",
                        "ANY",
                        1,
                        "source=parser;severity=MEDIUM"
                )
        ));
    }

    private void seedWorkflowRules() {
        if (workflowRuleRepository.count() > 0) {
            return;
        }
        WorkflowRule abnormalRouteRule = new WorkflowRule();
        abnormalRouteRule.setId(UUID.randomUUID());
        abnormalRouteRule.setTaskType("ROUTE_CHANGE");
        abnormalRouteRule.setTriggerField("requiresAbnormalReview");
        abnormalRouteRule.setExpectedValue("true");
        abnormalRouteRule.setNextTaskTypes("ABNORMAL_DATA_REVIEW");
        abnormalRouteRule.setPriority(1);
        abnormalRouteRule.setEnabled(true);
        abnormalRouteRule.setCreatedAt(Instant.now());
        abnormalRouteRule.setUpdatedAt(Instant.now());

        WorkflowRule reminderRuleValidation = new WorkflowRule();
        reminderRuleValidation.setId(UUID.randomUUID());
        reminderRuleValidation.setTaskType("REMINDER_RULE_CONFIG");
        reminderRuleValidation.setTriggerField("requestedValue");
        reminderRuleValidation.setExpectedValue("15");
        reminderRuleValidation.setNextTaskTypes("ABNORMAL_DATA_REVIEW,ROUTE_CHANGE");
        reminderRuleValidation.setPriority(1);
        reminderRuleValidation.setEnabled(true);
        reminderRuleValidation.setCreatedAt(Instant.now());
        reminderRuleValidation.setUpdatedAt(Instant.now());

        workflowRuleRepository.saveAll(List.of(abnormalRouteRule, reminderRuleValidation));
    }

    private void seedRecoveryCode(User user) {
        if (!recoveryCodeRepository.findByUserIdAndUsedFalse(user.getId()).isEmpty()) {
            return;
        }
        RecoveryCode recoveryCode = new RecoveryCode();
        recoveryCode.setId(UUID.randomUUID());
        recoveryCode.setUserId(user.getId());
        recoveryCode.setCodeHash(passwordEncoder.encode(seededRecoveryCode(user.getUsername())));
        recoveryCode.setUsed(false);
        recoveryCode.setCreatedAt(Instant.now());
        recoveryCodeRepository.save(recoveryCode);
    }

    private User user(String username, String password, String displayName, RoleName roleName) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(displayName);
        user.setRoleName(roleName);
        user.setTemporaryPassword(false);
        user.setActive(true);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        return user;
    }

    private Stop stop(String name, String pinyin, String initials, String keywords, String address, int popularity) {
        Stop stop = new Stop();
        stop.setId(UUID.randomUUID());
        stop.setStopName(name);
        stop.setStopNamePinyin(pinyin);
        stop.setStopInitials(initials);
        stop.setKeywordBlob(keywords);
        stop.setAddress(address);
        stop.setPopularity(popularity);
        stop.setActive(true);
        return stop;
    }

    private NotificationTemplate template(String key, String title, String content) {
        NotificationTemplate template = new NotificationTemplate();
        template.setId(UUID.randomUUID());
        template.setTemplateKey(key);
        template.setTitleTemplate(title);
        template.setContentTemplate(content);
        template.setUpdatedAt(Instant.now());
        return template;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private CleaningRule cleaningRule(String ruleKey, String fieldName, String pattern, String replacementValue) {
        CleaningRule rule = new CleaningRule();
        rule.setId(UUID.randomUUID());
        rule.setRuleKey(ruleKey);
        rule.setFieldName(fieldName);
        rule.setPattern(pattern);
        rule.setReplacementValue(replacementValue);
        rule.setEnabled(true);
        rule.setUpdatedAt(Instant.now());
        return rule;
    }

    private void ensureRouteStopLinks() {
        Long linkCount = jdbcTemplate.queryForObject("select count(*) from route_stops", Long.class);
        if (linkCount != null && linkCount > 0) {
            return;
        }
        Route route101 = routeRepository.findAll().stream().filter(route -> "101".equals(route.getRouteNumber())).findFirst().orElse(null);
        Route route18A = routeRepository.findAll().stream().filter(route -> "18A".equals(route.getRouteNumber())).findFirst().orElse(null);
        Stop central = stopRepository.findAll().stream().filter(stop -> "Central Station".equals(stop.getStopName())).findFirst().orElse(null);
        Stop riverside = stopRepository.findAll().stream().filter(stop -> "Riverside Park".equals(stop.getStopName())).findFirst().orElse(null);
        Stop oldTown = stopRepository.findAll().stream().filter(stop -> "Old Town North".equals(stop.getStopName())).findFirst().orElse(null);
        if (route101 != null && central != null) {
            jdbcTemplate.update("insert into route_stops(route_id, stop_id) values (?, ?)", route101.getId(), central.getId());
        }
        if (route101 != null && riverside != null) {
            jdbcTemplate.update("insert into route_stops(route_id, stop_id) values (?, ?)", route101.getId(), riverside.getId());
        }
        if (route18A != null && oldTown != null) {
            jdbcTemplate.update("insert into route_stops(route_id, stop_id) values (?, ?)", route18A.getId(), oldTown.getId());
        }
    }

    private String seededPassword(String username) {
        String envKey = "BOOTSTRAP_" + username.toUpperCase() + "_PASSWORD";
        String value = environment.getProperty(envKey);
        if (value == null || value.isBlank()) {
            value = environment.getProperty("bootstrap." + username + ".password");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(envKey + " must be provided for initial bootstrap");
        }
        return value;
    }

    private String seededRecoveryCode(String username) {
        String envKey = "BOOTSTRAP_" + username.toUpperCase() + "_RECOVERY_CODE";
        String value = environment.getProperty(envKey);
        if (value == null || value.isBlank()) {
            value = environment.getProperty("bootstrap." + username + ".recovery-code");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(envKey + " must be provided for initial bootstrap");
        }
        return value;
    }

    private WorkflowTask workflowTask(String title, String taskType, String approvalMode, int requiredApprovals, String payload) {
        WorkflowTask task = new WorkflowTask();
        task.setId(UUID.randomUUID());
        task.setTitle(title);
        task.setTaskType(taskType);
        task.setState(WorkflowState.PENDING);
        task.setOwnerRole(RoleName.DISPATCHER.name());
        task.setApprovalMode(approvalMode);
        task.setRequiredApprovals(requiredApprovals);
        task.setApprovalCount(0);
        task.setCurrentApprovals(0);
        task.setProgressStep(1);
        task.setProgressTotal(Math.max(1, requiredApprovals));
        task.setEscalated(false);
        task.setResubmissionCount(0);
        task.setPayload(payload);
        task.setCreatedAt(Instant.now().minusSeconds(3600));
        task.setUpdatedAt(Instant.now());
        return task;
    }
}
