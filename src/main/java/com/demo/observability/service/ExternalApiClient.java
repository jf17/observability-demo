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
public class ExternalApiClient implements ExternalApiClientService {

    private final Timer firstApiTimer;
    private final Timer secondApiTimer;
    private final Timer sendResultTimer;

    @Observed(name = "first.api.call", contextualName = "first-api-call")
    public Map<String, Object> callFirstApi() {
        return firstApiTimer.record(() -> {
            int delay = ThreadLocalRandom.current().nextInt(1200, 1501);
            log.debug("Calling first external API, delay: {} ms", delay);
            sleep(delay);
            double value = ThreadLocalRandom.current().nextDouble(10, 100);
            log.debug("First API responded with value: {}", value);
            return Map.of("source", "first-api", "value", value, "delayMs", delay, "status", "ok");
        });
    }

    @Observed(name = "second.api.call", contextualName = "second-api-call")
    public Map<String, Object> callSecondApi() {
        return secondApiTimer.record(() -> {
            int delay = ThreadLocalRandom.current().nextInt(800, 1201);
            log.debug("Calling second external API, delay: {} ms", delay);
            sleep(delay);
            double value = ThreadLocalRandom.current().nextDouble(10, 100);
            log.debug("Second API responded with value: {}", value);
            return Map.of("source", "second-api", "value", value, "delayMs", delay, "status", "ok");
        });
    }

    @Observed(name = "send.result", contextualName = "send-result")
    public Map<String, Object> sendResult(Map<String, Object> calculation) {
        return sendResultTimer.record(() -> {
            int delay = ThreadLocalRandom.current().nextInt(300, 601);
            log.debug("Sending result to third API, delay: {} ms", delay);
            sleep(delay);
            log.debug("Result sent successfully");
            return Map.of("destination", "third-api", "payload", calculation, "delayMs", delay, "status", "ok");
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
