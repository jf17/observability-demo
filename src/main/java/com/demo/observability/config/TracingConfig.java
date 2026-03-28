package com.demo.observability.config;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация трассировки (distributed tracing).
 *
 * ЧТО ТАКОЕ ТРАССИРОВКА?
 * Трассировка — это способ отследить путь одного запроса через всю систему.
 * Каждый запрос получает уникальный traceId. Внутри запроса каждый этап
 * (вызов метода, обращение к БД, вызов внешнего сервиса) — это отдельный span.
 *
 * Пример одного трейса:
 *   traceId: abc123
 *   ├── span: "http get /api/test"   (root span, ~3400ms)
 *   │   ├── span: "first-api-call"   (дочерний span, ~1380ms)
 *   │   ├── span: "second-api-call"  (дочерний span, ~1000ms)
 *   │   ├── span: "calculation"      (дочерний span, ~93ms)
 *   │   └── span: "send-result"      (дочерний span, ~450ms)
 *
 * Это позволяет в Grafana Tempo увидеть waterfall-диаграмму и сразу понять,
 * где именно теряется время.
 *
 * ЧТО ТАКОЕ @Observed?
 * Аннотация @Observed на методе говорит: "создай span для этого метода".
 * Но сама по себе аннотация не работает — нужен AOP (Aspect-Oriented Programming).
 * AOP — это механизм, который "оборачивает" вызов метода дополнительной логикой
 * (в нашем случае — созданием span-а) без изменения самого метода.
 *
 * КАК ЭТО РАБОТАЕТ ТЕХНИЧЕСКИ?
 * 1. Spring видит @Observed на методе бина
 * 2. ObservedAspect перехватывает вызов (через JDK dynamic proxy или CGLIB proxy)
 * 3. Перед вызовом создаётся новый Observation (span), дочерний к текущему активному
 * 4. После вызова Observation закрывается — span отправляется в Tempo через OTLP
 *
 * БЕЗ ЭТОГО БИНА @Observed работать не будет — span-ы не создадутся,
 * и в Tempo будет виден только root span "http get /api/test".
 *
 * ПОЧЕМУ НУЖЕН spring-boot-starter-aop В build.gradle?
 * ObservedAspect использует AspectJ для перехвата вызовов методов.
 * Без зависимости spring-boot-starter-aop аспект не будет применён.
 */
@Configuration
public class TracingConfig {

    /**
     * ObservedAspect — AOP-аспект, который обрабатывает аннотацию @Observed.
     *
     * ObservationRegistry — центральный реестр наблюдений Micrometer.
     * Через него создаются Observation-объекты, которые одновременно:
     * - создают span для трейсинга (→ Tempo)
     * - записывают метрику (→ Prometheus)
     *
     * Spring Boot автоматически создаёт и настраивает ObservationRegistry —
     * нам нужно только передать его в ObservedAspect.
     */
    @Bean
    public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        return new ObservedAspect(observationRegistry);
    }
}
