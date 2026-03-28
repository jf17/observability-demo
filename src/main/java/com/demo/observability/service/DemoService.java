package com.demo.observability.service;

import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Сервис с двумя методами, демонстрирующими observability на уровне бизнес-логики.
 *
 * ЗАЧЕМ ИЗМЕРЯТЬ КАЖДЫЙ ЭТАП ОТДЕЛЬНО?
 * Представь: запрос выполняется 2 секунды. Без детальных метрик непонятно почему.
 * С отдельными Timer-ами сразу видно:
 *   - external.call = 1800ms  ← вот где проблема
 *   - calculation   = 100ms   ← это норма
 *
 * СВЯЗЬ МЕЖДУ TIMER И SPAN:
 * - Timer (Micrometer) → метрики → Prometheus → Grafana (графики, агрегаты за время)
 * - @Observed (Tracing) → span-ы → Tempo → Grafana (waterfall конкретного запроса)
 * Они дополняют друг друга: метрики показывают тренды, трейсы — детали конкретного запроса.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoService {

    // Timer-ы инжектируются из ObservabilityConfig
    private final Timer externalCallTimer;
    private final Timer calculationTimer;

    /**
     * Эмулирует вызов внешнего API с задержкой 1200–1500 ms.
     *
     * ЧТО ДЕЛАЕТ @Observed?
     * Аннотация говорит ObservedAspect (из TracingConfig): "создай span для этого метода".
     *
     *   name = "external.call"       — имя метрики наблюдения (используется Micrometer)
     *   contextualName = "external-call" — имя span-а, которое видно в Grafana Tempo
     *
     * Когда этот метод вызывается внутри активного трейса (а он всегда вызывается
     * из контроллера, где уже есть root span), создаётся ДОЧЕРНИЙ span.
     * Дочерний span наследует traceId родителя, но имеет свой spanId.
     *
     * В Tempo это выглядит так:
     *   http get /api/test  [====================] 1600ms  ← root span
     *     external-call     [==================]   1380ms  ← этот span
     *
     * КАК РАБОТАЕТ ДВОЙНОЕ ИЗМЕРЕНИЕ (Timer + @Observed)?
     * @Observed создаёт span для трейсинга (виден в Tempo).
     * externalCallTimer.record() записывает метрику (видна в Prometheus/Grafana).
     * Оба измерения происходят одновременно — это нормальная практика.
     */
    @Observed(name = "external.call", contextualName = "external-call")
    public Map<String, Object> callExternalApi() {

        // Timer.record() принимает Supplier<T> — лямбду, которая возвращает результат.
        // Timer замеряет время выполнения лямбды и записывает его в метрику.
        return externalCallTimer.record(() -> {
            int delay = ThreadLocalRandom.current().nextInt(1200, 1501);

            // В этом логе spanId будет другим, чем в контроллере —
            // мы находимся внутри дочернего span-а "external-call"
            log.debug("Calling external API, simulated delay: {} ms", delay);

            try {
                // Имитируем медленный внешний сервис
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                // Восстанавливаем флаг прерывания потока — best practice при InterruptedException
                Thread.currentThread().interrupt();
                throw new RuntimeException("External call interrupted", e);
            }

            log.debug("External API call completed");

            // Возвращаем результат — в реальном приложении здесь был бы ответ от HTTP клиента
            return Map.of(
                    "source", "external-api",
                    "delayMs", delay,
                    "status", "ok"
            );
        });
    }

    /**
     * Эмулирует вычисления с задержкой 50–150 ms.
     *
     * Принцип тот же, что и в callExternalApi():
     * - @Observed создаёт span "calculation" в Tempo
     * - calculationTimer.record() записывает метрику в Prometheus
     *
     * В Tempo waterfall:
     *   http get /api/test  [====================] 1600ms
     *     external-call     [==================]   1380ms
     *     calculation                [====]          93ms  ← этот span
     *
     * Обрати внимание: calculation начинается ПОСЛЕ завершения external-call,
     * потому что вызовы последовательные (не параллельные).
     */
    @Observed(name = "calculation", contextualName = "calculation")
    public Map<String, Object> performCalculation() {
        return calculationTimer.record(() -> {
            int delay = ThreadLocalRandom.current().nextInt(50, 151);
            log.debug("Performing calculation, simulated delay: {} ms", delay);

            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Calculation interrupted", e);
            }

            log.debug("Calculation completed");

            return Map.of(
                    "result", ThreadLocalRandom.current().nextDouble(0, 100),
                    "delayMs", delay,
                    "status", "ok"
            );
        });
    }
}
