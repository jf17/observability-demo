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

/**
 * REST контроллер — точка входа для всех запросов к /api/test.
 *
 * КАК РАБОТАЕТ ТРАССИРОВКА НА УРОВНЕ КОНТРОЛЛЕРА?
 * Spring Boot автоматически создаёт root span для каждого входящего HTTP запроса.
 * Root span — это "корневой" span, родитель для всех дочерних span-ов внутри запроса.
 * Его имя будет "http get /api/test" — видно в Grafana Tempo.
 *
 * КАК traceId ПОПАДАЕТ В ЛОГИ?
 * Micrometer Tracing автоматически кладёт traceId и spanId в MDC (Mapped Diagnostic Context).
 * MDC — это thread-local хранилище, из которого Logback берёт значения при форматировании лога.
 * В application.yml настроен паттерн: [traceId=%X{traceId} spanId=%X{spanId}]
 * Поэтому каждая строка лога содержит идентификаторы трейса — можно найти все логи
 * конкретного запроса по его traceId.
 *
 * ПОЧЕМУ КОНТРОЛЛЕР ЗАВИСИТ ОТ ИНТЕРФЕЙСА BusinessService, А НЕ ОТ DemoService?
 * Контроллер не должен знать о деталях реализации. Он знает только контракт (интерфейс).
 * Это позволяет подменить реализацию без изменения контроллера — например, в тестах.
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TestController {

    // Инжектируем через интерфейс — Spring найдёт реализацию DemoService автоматически
    private final BusinessService businessService;

    /**
     * apiTotalTimer — инжектируется из ObservabilityConfig.
     * Измеряет полное время обработки запроса, включая все этапы бизнес-процесса.
     */
    private final Timer apiTotalTimer;

    /**
     * Обрабатывает GET /api/test.
     *
     * ПОЧЕМУ НЕТ @Observed НА КОНТРОЛЛЕРЕ?
     * Spring MVC уже создаёт root span автоматически для каждого HTTP запроса.
     * Добавлять @Observed сверху — значит создавать лишний дублирующий span.
     * Дочерние span-ы создаются в сервисах через @Observed.
     *
     * КАК РАБОТАЕТ apiTotalTimer.record()?
     * Это эквивалентно:
     *   long start = System.nanoTime();
     *   ... выполнить код ...
     *   timer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
     * Но Timer.record() делает это атомарно и потокобезопасно.
     * Результат записывается в Prometheus как гистограмма — можно считать p50/p95/p99.
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> test() {
        // В этот момент traceId уже есть в MDC — Spring создал root span до входа в метод.
        // В логе увидим: [traceId=abc123 spanId=def456] Received request to /api/test
        log.info("Received request to /api/test");

        Map<String, Object> response = apiTotalTimer.record(() -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("timestamp", Instant.now().toString());
            body.put("status", "success");
            // Весь бизнес-процесс делегируется сервису — контроллер только принимает и отдаёт ответ
            body.putAll(businessService.process());
            return body;
        });

        // После этого лога root span закроется — Spring MVC завершит его при отправке ответа.
        // В Tempo будет видно полное время от начала до конца запроса.
        log.info("Request to /api/test completed");
        return ResponseEntity.ok(response);
    }
}
