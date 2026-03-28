package com.demo.observability.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DemoService implements BusinessService {

    private final ExternalApiClientService apiClient;
    private final CalculationServiceApi calculationService;

    public Map<String, Object> process() {
        log.info("Starting business process");

        Map<String, Object> first = apiClient.callFirstApi();
        Map<String, Object> second = apiClient.callSecondApi();
        Map<String, Object> calculation = calculationService.performCalculation(first, second);
        Map<String, Object> send = apiClient.sendResult(calculation);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("firstApi", first);
        result.put("secondApi", second);
        result.put("calculation", calculation);
        result.put("sendResult", send);

        log.info("Business process completed");
        return result;
    }
}
