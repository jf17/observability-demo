package com.demo.observability.service;

import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Эмуляция вызовов внешних API с измерением времени через Micrometer Timer и @Observed.
 *
 * ЗАЧЕМ ИЗМЕРЯТЬ КАЖДЫЙ ЭТАП ОТДЕЛЬНО?
 * Представь: запрос выполняется 3.5 секунды. Без детальных метрик непонятно почему.
 * С отдельными Timer-ами сразу видно:
 *   - first.api.call  = 1380ms  ← медленный внешний сервис
 *   - second.api.call = 1000ms  ← тоже медленный
 *   - calculation     =   93ms  ← норма
 *   - send.result     =  450ms  ← приемлемо
 *
 * СВЯЗЬ МЕЖДУ TIMER И @Observed:
 * - Timer (Micrometer) → метрики → Prometheus → Grafana (графики, агрегаты за время)
 * - @Observed (Tracing) → span-ы → Tempo → Grafana (waterfall конкретного запроса)
 * Они дополняют друг друга: метрики показывают тренды, трейсы — детали конкретного запроса.
 *
 * КАК РАБОТАЕТ @Observed?
 * 1. Spring видит @Observed на методе
 * 2. ObservedAspect (из TracingConfig) перехватывает вызов через AOP proxy
 * 3. Перед вызовом создаётся новый span, дочерний к текущему активному span-у
 * 4. После вызова span закрывается с временем выполнения
 * 5. Span отправляется в Tempo через OTLP HTTP на порт 4318
 *
 * name = "first.api.call"       — имя метрики наблюдения (используется Micrometer)
 * contextualName = "first-api-call" — имя span-а, которое видно в Grafana Tempo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalApiClient implements ExternalApiClientService {

    // Timer-ы инжектируются из ObservabilityConfig
    private final Timer firstApiTimer;
    private final Timer secondApiTimer;
    private final Timer sendResultTimer;

    /**
     * Эмулирует вызов первого стороннего API с задержкой 1200–1500 ms.
     *
     * В Tempo waterfall этот метод создаёт span "first-api-call":
     *   http get /api/test  [════════════════════════════════════]
     *     first-api-call    [══════════════════]  ← этот span
     *
     * КАК РАБОТАЕТ ДВОЙНОЕ ИЗМЕРЕНИЕ (Timer + @Observed)?
     * @Observed создаёт span для трейсинга (виден в Tempo).
     * firstApiTimer.record() записывает метрику (видна в Prometheus/Grafana).
     * Оба измерения происходят одновременно — это нормальная практика.
     *
     * Timer.record() принимает Supplier<T> — лямбду, которая возвращает результат.
     * Timer замеряет время выполнения лямбды и записывает его в метрику.
     */
    @Override
    @Observed(name = "first.api.call", contextualName = "first-api-call")
    public Map<String, Object> callFirstApi() {
        return firstApiTimer.record(() -> {
            int delay = ThreadLocalRandom.current().nextInt(1200, 1501);
            log.debug("Calling first external API, delay: {} ms", delay);
            sleep(delay);
            double value = ThreadLocalRandom.current().nextDouble(10, 100);
            log.debug("First API responded with value: {}", value);
            // В реальном приложении здесь был бы ответ от HTTP клиента (RestTemplate, WebClient и т.д.)
            return Map.of("source", "first-api", "value", value, "delayMs", delay, "status", "ok");
        });
    }

    /**
     * Эмулирует вызов второго стороннего API с задержкой 800–1200 ms.
     *
     * Принцип тот же, что и в callFirstApi().
     * В Tempo waterfall создаётся span "second-api-call" — начинается ПОСЛЕ завершения first-api-call,
     * потому что вызовы последовательные (не параллельные).
     */
    @Override
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

    /**
     * Эмулирует отправку результата вычислений в третий сторонний API с задержкой 300–600 ms.
     *
     * Вызывается последним — после того как calculation уже выполнен.
     * В Tempo waterfall создаётся span "send-result".
     *
     * @param calculation результат вычислений из CalculationService (sum, avg и т.д.)
     */
    @Override
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

    /**
     * Вспомогательный метод для эмуляции задержки сети/обработки.
     * Корректно обрабатывает прерывание потока — восстанавливает флаг interrupt,
     * чтобы вышестоящий код мог его обработать.
     */
    private void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            // Восстанавливаем флаг прерывания потока — best practice при InterruptedException
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted", e);
        }
    }
}
