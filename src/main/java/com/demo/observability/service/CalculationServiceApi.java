package com.demo.observability.service;

import java.util.Map;

/**
 * Интерфейс сервиса вычислений.
 *
 * Вынесен в отдельный бин по той же причине, что и ExternalApiClientService —
 * чтобы @Observed корректно создавал span через AOP proxy.
 * Подробное объяснение см. в javadoc ExternalApiClientService.
 */
public interface CalculationServiceApi {
    /**
     * Вычисляет сумму и среднее значение на основе ответов двух внешних API.
     *
     * @param first  результат первого API (содержит поле "value")
     * @param second результат второго API (содержит поле "value")
     * @return Map с полями: v1, v2, sum, avg, delayMs, status
     */
    Map<String, Object> performCalculation(Map<String, Object> first, Map<String, Object> second);
}
