package com.demo.observability.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация метрик Micrometer.
 *
 * ЧТО ТАКОЕ MICROMETER?
 * Micrometer — это библиотека для сбора метрик в Java-приложениях.
 * Метрика — это числовое измерение чего-либо: времени выполнения, количества запросов,
 * использования памяти и т.д. Micrometer умеет отправлять эти данные в разные системы
 * мониторинга (Prometheus, Datadog, InfluxDB и др.) через единый API.
 *
 * ЧТО ТАКОЕ TIMER?
 * Timer — один из типов метрик Micrometer. Он измеряет:
 *   - COUNT  — сколько раз был вызван (количество запросов)
 *   - TOTAL  — суммарное время всех вызовов
 *   - MAX    — максимальное время одного вызова
 *   - гистограмму — распределение времён (нужна для p95, p99 и т.д.)
 *
 * ЗАЧЕМ РЕГИСТРИРОВАТЬ TIMER-Ы ЗАРАНЕЕ?
 * Если создать Timer только при первом вызове метода, то Prometheus не увидит метрику
 * до первого запроса. Это проблема: дашборд в Grafana будет пустым при старте.
 * Регистрация в @Configuration гарантирует, что метрики появятся сразу при запуске
 * приложения — даже если ни одного запроса ещё не было.
 */
@Configuration
public class ObservabilityConfig {

    /**
     * Timer для измерения полного времени обработки запроса /api/test.
     *
     * Timer.builder("api.total") — имя метрики. В Prometheus она станет
     * api_total_seconds_count, api_total_seconds_sum, api_total_seconds_bucket
     * (точки заменяются на подчёркивания, добавляется суффикс _seconds).
     *
     * .tag("endpoint", "/api/test") — тег позволяет фильтровать метрику в Prometheus/Grafana.
     * Например: api_total_seconds_count{endpoint="/api/test"}
     * Теги — это как метки, которые добавляют контекст к числу.
     *
     * .publishPercentileHistogram() — включает гистограмму. Без неё нельзя посчитать
     * перцентили (p95, p99). Гистограмма разбивает все измерения по "корзинам" (buckets):
     * сколько запросов уложилось в 10ms, 50ms, 100ms, 500ms и т.д.
     */
    @Bean
    public Timer apiTotalTimer(MeterRegistry registry) {
        return Timer.builder("api.total")
                .description("Total time of /api/test endpoint")
                .tag("endpoint", "/api/test")
                .publishPercentileHistogram()
                .register(registry); // регистрируем в глобальном реестре метрик
    }

    /**
     * Timer для измерения времени вызова внешнего API.
     *
     * Отдельный Timer для каждого этапа позволяет точно понять,
     * какой именно этап является узким местом (bottleneck).
     * Например, если api.total = 1500ms, а external.call = 1400ms —
     * проблема явно во внешнем сервисе.
     */
    @Bean
    public Timer externalCallTimer(MeterRegistry registry) {
        return Timer.builder("external.call")
                .description("Time spent in emulated external API call")
                .tag("endpoint", "/api/test")
                .publishPercentileHistogram()
                .register(registry);
    }

    /**
     * Timer для измерения времени вычислений.
     */
    @Bean
    public Timer calculationTimer(MeterRegistry registry) {
        return Timer.builder("calculation")
                .description("Time spent in calculation step")
                .tag("endpoint", "/api/test")
                .publishPercentileHistogram()
                .register(registry);
    }
}
