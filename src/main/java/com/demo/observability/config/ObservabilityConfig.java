package com.demo.observability.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservabilityConfig {

    @Bean
    public Timer apiTotalTimer(MeterRegistry registry) {
        return Timer.builder("api.total")
                .description("Total time of /api/test endpoint")
                .tag("endpoint", "/api/test")
                .publishPercentileHistogram()
                .register(registry);
    }

    @Bean
    public Timer firstApiTimer(MeterRegistry registry) {
        return Timer.builder("first.api.call")
                .description("Time spent calling first external API")
                .publishPercentileHistogram()
                .register(registry);
    }

    @Bean
    public Timer secondApiTimer(MeterRegistry registry) {
        return Timer.builder("second.api.call")
                .description("Time spent calling second external API")
                .publishPercentileHistogram()
                .register(registry);
    }

    @Bean
    public Timer calculationTimer(MeterRegistry registry) {
        return Timer.builder("calculation")
                .description("Time spent in calculation step")
                .publishPercentileHistogram()
                .register(registry);
    }

    @Bean
    public Timer sendResultTimer(MeterRegistry registry) {
        return Timer.builder("send.result")
                .description("Time spent sending result to third external API")
                .publishPercentileHistogram()
                .register(registry);
    }
}
