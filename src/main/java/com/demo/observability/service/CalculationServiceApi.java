package com.demo.observability.service;

import java.util.Map;

public interface CalculationServiceApi {
    Map<String, Object> performCalculation(Map<String, Object> first, Map<String, Object> second);
}
