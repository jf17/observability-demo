package com.demo.observability.controller;

import com.demo.observability.service.BusinessService;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TestController {

    private final BusinessService businessService;
    private final Timer apiTotalTimer;

    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> test() {
        log.info("Received request to /api/test");

        Map<String, Object> response = apiTotalTimer.record(() -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("timestamp", Instant.now().toString());
            body.put("status", "success");
            body.putAll(businessService.process());
            return body;
        });

        log.info("Request to /api/test completed");
        return ResponseEntity.ok(response);
    }
}
