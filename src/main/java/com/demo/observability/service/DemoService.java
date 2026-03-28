package com.demo.observability.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Оркестратор бизнес-процесса — координирует все этапы обработки запроса.
 *
 * ЧТО ТАКОЕ ОРКЕСТРАТОР?
 * Оркестратор — это сервис, который знает ПОРЯДОК выполнения шагов, но не знает
 * деталей реализации каждого шага. Он вызывает другие сервисы и собирает результат.
 * Сам оркестратор не содержит бизнес-логики — только последовательность вызовов.
 *
 * ПОЧЕМУ ЗДЕСЬ НЕТ @Observed?
 * DemoService сам является оркестратором — его время выполнения = сумма всех этапов.
 * Полное время уже измеряется через apiTotalTimer в контроллере.
 * Добавлять ещё один span для DemoService было бы избыточным дублированием.
 *
 * КАК РАБОТАЕТ ТРЕЙСИНГ В ЭТОМ КЛАССЕ?
 * Когда контроллер вызывает process(), уже существует root span "http get /api/test".
 * Каждый вызов apiClient.* и calculationService.* создаёт ДОЧЕРНИЙ span внутри этого трейса.
 * В Grafana Tempo это выглядит как waterfall:
 *
 *   http get /api/test  [════════════════════════════════════] ~3400ms  ← root span
 *     first-api-call    [══════════════════]                   ~1380ms  ← дочерний span
 *     second-api-call                      [════════════]      ~1000ms  ← дочерний span
 *     calculation                                       [══]     ~93ms  ← дочерний span
 *     send-result                                          [═══] ~450ms  ← дочерний span
 *
 * @Slf4j — Lombok генерирует: private static final Logger log = LoggerFactory.getLogger(...)
 * @RequiredArgsConstructor — Lombok генерирует конструктор для всех final полей (DI через конструктор)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoService implements BusinessService {

    // Инжектируем через интерфейсы — Spring сам найдёт реализации ExternalApiClient и CalculationService
    private final ExternalApiClientService apiClient;
    private final CalculationServiceApi calculationService;

    /**
     * Выполняет четыре последовательных этапа:
     * 1. Вызов первого внешнего API (1200–1500 ms)
     * 2. Вызов второго внешнего API (800–1200 ms)
     * 3. Вычисление суммы и среднего из двух ответов (50–150 ms)
     * 4. Отправка результата в третий API (300–600 ms)
     *
     * Каждый вызов идёт через Spring AOP proxy — @Observed на методах
     * apiClient и calculationService корректно создаёт дочерние span-ы.
     */
    @Override
    public Map<String, Object> process() {
        log.info("Starting business process");

        // Каждый вызов через интерфейс → через AOP proxy → @Observed создаёт span
        Map<String, Object> first = apiClient.callFirstApi();
        Map<String, Object> second = apiClient.callSecondApi();
        Map<String, Object> calculation = calculationService.performCalculation(first, second);
        Map<String, Object> send = apiClient.sendResult(calculation);

        // LinkedHashMap сохраняет порядок вставки — важно для читаемого JSON-ответа
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("firstApi", first);
        result.put("secondApi", second);
        result.put("calculation", calculation);
        result.put("sendResult", send);

        log.info("Business process completed");
        return result;
    }
}
