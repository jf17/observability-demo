package com.demo.observability.service;

import java.util.Map;

/**
 * Интерфейс для эмуляции вызовов внешних API.
 *
 * ПОЧЕМУ ВЫЗОВЫ ВНЕШНИХ API ВЫНЕСЕНЫ В ОТДЕЛЬНЫЙ БИН?
 * Это связано с тем, как работает Spring AOP и аннотация @Observed.
 *
 * @Observed создаёт span (единицу трейса) для метода через AOP proxy.
 * AOP proxy — это обёртка, которую Spring создаёт поверх твоего бина.
 * Когда ты вызываешь метод через proxy, Spring перехватывает вызов и добавляет
 * дополнительную логику (создание span-а).
 *
 * НО ЕСТЬ ПРОБЛЕМА — self-invocation:
 * Если метод A() вызывает метод B() внутри ОДНОГО бина, вызов B() идёт напрямую,
 * минуя proxy. Spring не перехватывает такой вызов, и @Observed на B() не работает.
 *
 * РЕШЕНИЕ:
 * Вынести методы в отдельный бин. Тогда вызов идёт через proxy другого бина,
 * и @Observed корректно создаёт span.
 *
 * Схема:
 *   DemoService → (через proxy) → ExternalApiClient.callFirstApi()  ✅ span создаётся
 *   DemoService → (напрямую)    → this.callFirstApi()               ❌ span не создаётся
 */
public interface ExternalApiClientService {
    /** Эмуляция первого стороннего API — задержка 1200–1500 ms */
    Map<String, Object> callFirstApi();

    /** Эмуляция второго стороннего API — задержка 800–1200 ms */
    Map<String, Object> callSecondApi();

    /** Эмуляция отправки результата в третий API — задержка 300–600 ms */
    Map<String, Object> sendResult(Map<String, Object> calculation);
}
