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
 *   ├── span: "http get /api/test"     (root span, 1500ms)
 *   │   ├── span: "external-call"      (дочерний span, 1380ms)
 *   │   └── span: "calculation"        (дочерний span, 93ms)
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
 * КАК ЭТО РАБОТАЕТ?
 * 1. Spring видит @Observed на методе
 * 2. ObservedAspect перехватывает вызов (через AOP proxy)
 * 3. Перед вызовом создаётся новый span, дочерний к текущему
 * 4. После вызова span закрывается с временем выполнения
 * 5. Span отправляется в Tempo через OTLP
 *
 * БЕЗ ЭТОГО БИНА @Observed работать не будет — span-ы не создадутся.
 */
@Configuration
public class TracingConfig {

    /**
     * ObservedAspect — это AOP-аспект, который обрабатывает аннотацию @Observed.
     * ObservationRegistry — центральный реестр наблюдений Micrometer,
     * через который создаются span-ы и метрики.
     */
    @Bean
    public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        return new ObservedAspect(observationRegistry);
    }
}
