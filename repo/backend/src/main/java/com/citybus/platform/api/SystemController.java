package com.citybus.platform.api;

import com.citybus.platform.application.AppProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemController {
    private final AppProperties properties;

    public SystemController(AppProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/version")
    public Map<String, Object> version() {
        return Map.of("version", properties.version(), "serverTime", Instant.now().toString());
    }
}
