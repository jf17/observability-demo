package com.demo.observability.service;

import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Сервис вычислений — обрабатывает данные, полученные от двух внешних API.
 *
 * ЗАЧЕМ ОТДЕЛЬНЫЙ БИН ДЛЯ ВЫЧИСЛЕНИЙ?
 * Вычисления вызываются из DemoService. Если бы performCalculation() был методом
 * самого DemoService, то вызов шёл бы напрямую (self-invocation), минуя AOP proxy.
 * В таком случае @Observed не создал бы span, и в Tempo не было бы видно этого этапа.
 *
 * Вынося метод в отдельный бин CalculationService, мы гарантируем, что вызов
 * из DemoService идёт через Spring AOP proxy → @Observed корректно создаёт span.
 *
 * ИТОГО В TEMPO WATERFALL ВИДНЫ ВСЕ 4 ЭТАПА:
 *   http get /api/test  [════════════════════════════════════] ~3400ms
 *     first-api-call    [══════════════════]                   ~1380ms
 *     second-api-call                      [════════════]      ~1000ms
 *     calculation                                       [══]     ~93ms  ← этот span
 *     send-result                                          [═══] ~450ms
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalculationService implements CalculationServiceApi {

    // Timer инжектируется из ObservabilityConfig
    private final Timer calculationTimer;

    /**
     * Вычисляет сумму и среднее значение из ответов двух внешних API.
     *
     * ЧТО ДЕЛАЕТ @Observed?
     * name = "calculation"         — имя метрики наблюдения
     * contextualName = "calculation" — имя span-а в Grafana Tempo
     *
     * Когда этот метод вызывается из DemoService (через proxy), создаётся дочерний span.
     * Дочерний span наследует traceId родительского span-а, но имеет свой уникальный spanId.
     * Это позволяет в Tempo увидеть точное время вычислений внутри общего трейса запроса.
     *
     * КАК ЧИТАТЬ ЛОГИ С traceId?
     * В логах каждой строки есть [traceId=abc123 spanId=def456].
     * spanId в этом методе будет ДРУГИМ, чем в контроллере — мы находимся
     * внутри дочернего span-а "calculation". Но traceId — тот же самый.
     * По traceId можно найти все логи одного запроса в любой системе логирования.
     *
     * @param first  результат callFirstApi() — содержит поле "value" (double)
     * @param second результат callSecondApi() — содержит поле "value" (double)
     */
    @Override
    @Observed(name = "calculation", contextualName = "calculation")
    public Map<String, Object> performCalculation(Map<String, Object> first, Map<String, Object> second) {
        return calculationTimer.record(() -> {
            int delay = ThreadLocalRandom.current().nextInt(50, 151);
            log.debug("Performing calculation, delay: {} ms", delay);
            sleep(delay);

            // Извлекаем значения из ответов API
            double v1 = (double) first.get("value");
            double v2 = (double) second.get("value");
            double sum = v1 + v2;
            double avg = sum / 2;

            log.debug("Calculation done: v1={}, v2={}, sum={}, avg={}", v1, v2, sum, avg);

            // Результат передаётся дальше в sendResult() для отправки в третий API
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
