package com.citybus.platform.api;

import com.citybus.platform.api.dto.AdminDtos;
import com.citybus.platform.application.AdminService;
import com.citybus.platform.infrastructure.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/templates")
    public List<AdminDtos.TemplateResponse> templates() {
        return adminService.templates();
    }

    @PutMapping("/templates/{id}")
    public AdminDtos.TemplateResponse updateTemplate(@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable UUID id, @Valid @RequestBody AdminDtos.TemplateUpdateRequest request) {
        return adminService.updateTemplate(currentUser, id, request);
    }

    @GetMapping("/search-config")
    public AdminDtos.SearchConfigResponse searchConfig() {
        return adminService.searchConfig();
    }

    @PutMapping("/search-config")
    public AdminDtos.SearchConfigResponse updateSearchConfig(@AuthenticationPrincipal AuthenticatedUser currentUser, @Valid @RequestBody AdminDtos.SearchConfigUpdateRequest request) {
        return adminService.updateSearchConfig(currentUser, request);
    }

    @GetMapping("/dictionaries")
    public List<AdminDtos.DictionaryGroupResponse> dictionaries() {
        return adminService.dictionaries();
    }

    @PutMapping("/dictionaries/{id}")
    public AdminDtos.DictionaryResponse updateDictionary(@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable UUID id, @Valid @RequestBody AdminDtos.DictionaryUpdateRequest request) {
        return adminService.updateDictionary(currentUser, id, request);
    }

    @GetMapping("/cleaning-rules")
    public List<AdminDtos.CleaningRuleResponse> cleaningRules() {
        return adminService.cleaningRules();
    }

    @PutMapping("/cleaning-rules/{id}")
    public AdminDtos.CleaningRuleResponse updateCleaningRule(@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable UUID id, @Valid @RequestBody AdminDtos.CleaningRuleUpdateRequest request) {
        return adminService.updateCleaningRule(currentUser, id, request);
    }

    @GetMapping("/parsing/templates")
    public List<AdminDtos.ParsingTemplateResponse> parsingTemplates() {
        return adminService.parsingTemplates();
    }

    @PostMapping("/parsing/templates")
    public AdminDtos.ParsingTemplateResponse saveParsingTemplate(@AuthenticationPrincipal AuthenticatedUser currentUser, @Valid @RequestBody AdminDtos.ParsingTemplateRequest request) {
        return adminService.saveParsingTemplate(currentUser, request);
    }

    @PostMapping("/parsing/parse")
    public AdminDtos.ParsedRecordResponse parse(@AuthenticationPrincipal AuthenticatedUser currentUser, @Valid @RequestBody AdminDtos.ParseRequest request) {
        return adminService.parseSource(currentUser, request);
    }

    @GetMapping("/alerts")
    public List<AdminDtos.AlertResponse> alerts() {
        return adminService.alerts();
    }

    @GetMapping("/reports")
    public List<AdminDtos.DiagnosticReportResponse> reports() {
        return adminService.reports();
    }

    @GetMapping("/reports/{id}")
    public AdminDtos.DiagnosticReportResponse report(@PathVariable UUID id) {
        return adminService.report(id);
    }

    @GetMapping("/users")
    public List<AdminDtos.UserSummary> users() {
        return adminService.users();
    }

    @PostMapping("/users/reset-password")
    public AdminDtos.PasswordResetIssueResponse resetPassword(@AuthenticationPrincipal AuthenticatedUser currentUser, @Valid @RequestBody AdminDtos.ResetPasswordRequest request) {
        return adminService.resetUserPassword(currentUser, request.username());
    }

    @PostMapping("/users/reset-password/reveal")
    public Map<String, String> revealTemporaryPassword(@AuthenticationPrincipal AuthenticatedUser currentUser, @Valid @RequestBody AdminDtos.PasswordResetTokenRequest request) {
        return Map.of("temporaryPassword", adminService.consumePasswordResetToken(currentUser, request.requestToken()));
    }
}
