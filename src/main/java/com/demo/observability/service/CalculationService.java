package com.demo.observability.service;

import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class CalculationService implements CalculationServiceApi {

    private final Timer calculationTimer;

    @Observed(name = "calculation", contextualName = "calculation")
    public Map<String, Object> performCalculation(Map<String, Object> first, Map<String, Object> second) {
        return calculationTimer.record(() -> {
            int delay = ThreadLocalRandom.current().nextInt(50, 151);
            log.debug("Performing calculation, delay: {} ms", delay);
            sleep(delay);

            double v1 = (double) first.get("value");
            double v2 = (double) second.get("value");
            double sum = v1 + v2;
            double avg = sum / 2;

            log.debug("Calculation done: v1={}, v2={}, sum={}, avg={}", v1, v2, sum, avg);
            return Map.of("v1", v1, "v2", v2, "sum", sum, "avg", avg, "delayMs", delay, "status", "ok");
        });
    }

    private void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted", e);
        }
    }
}
