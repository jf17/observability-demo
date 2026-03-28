package com.demo.observability.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация метрик Micrometer — регистрация Timer-ов для каждого этапа бизнес-процесса.
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
 *   - гистограмму — распределение времён (нужна для p50/p95/p99)
 *
 * ЗАЧЕМ РЕГИСТРИРОВАТЬ TIMER-Ы ЗАРАНЕЕ?
 * Если создать Timer только при первом вызове метода, то Prometheus не увидит метрику
 * до первого запроса. Это проблема: дашборд в Grafana будет пустым при старте.
 * Регистрация в @Configuration гарантирует, что метрики появятся сразу при запуске
 * приложения — даже если ни одного запроса ещё не было.
 *
 * КАК ИМЕНА МЕТРИК ПРЕВРАЩАЮТСЯ В PROMETHEUS?
 * Точки заменяются на подчёркивания, добавляется суффикс _seconds:
 *   api.total       → api_total_seconds_count / _sum / _bucket
 *   first.api.call  → first_api_call_seconds_count / _sum / _bucket
 *   и т.д.
 */
@Configuration
public class ObservabilityConfig {

    /**
     * Timer для измерения полного времени обработки запроса /api/test.
     * Охватывает все 4 этапа: first API + second API + calculation + send result.
     *
     * .publishPercentileHistogram() — включает гистограмму. Без неё нельзя посчитать
     * перцентили (p95, p99). Гистограмма разбивает все измерения по "корзинам" (buckets):
     * сколько запросов уложилось в 100ms, 500ms, 1s, 2s и т.д.
     */
    @Bean
    public Timer apiTotalTimer(MeterRegistry registry) {
        return Timer.builder("api.total")
                .description("Total time of /api/test endpoint")
                .tag("endpoint", "/api/test")
                .publishPercentileHistogram()
                .register(registry);
    }

    /**
     * Timer для измерения времени вызова первого внешнего API (1200–1500 ms).
     * Если api.total внезапно вырос — смотришь на этот Timer, чтобы понять, виноват ли первый API.
     */
    @Bean
    public Timer firstApiTimer(MeterRegistry registry) {
        return Timer.builder("first.api.call")
                .description("Time spent calling first external API")
                .publishPercentileHistogram()
                .register(registry);
    }

    /**
     * Timer для измерения времени вызова второго внешнего API (800–1200 ms).
     */
    @Bean
    public Timer secondApiTimer(MeterRegistry registry) {
        return Timer.builder("second.api.call")
                .description("Time spent calling second external API")
                .publishPercentileHistogram()
                .register(registry);
    }

    /**
     * Timer для измерения времени вычислений (50–150 ms).
     * Обычно самый быстрый этап — если вдруг вырос, значит проблема в логике вычислений.
     */
    @Bean
    public Timer calculationTimer(MeterRegistry registry) {
        return Timer.builder("calculation")
                .description("Time spent in calculation step")
                .publishPercentileHistogram()
                .register(registry);
    }

    /**
     * Timer для измерения времени отправки результата в третий API (300–600 ms).
     */
    @Bean
    public Timer sendResultTimer(MeterRegistry registry) {
        return Timer.builder("send.result")
                .description("Time spent sending result to third external API")
                .publishPercentileHistogram()
                .register(registry);
    }
}
