package com.demo.observability.service;

import java.util.Map;

public interface ExternalApiClientService {
    Map<String, Object> callFirstApi();
    Map<String, Object> callSecondApi();
    Map<String, Object> sendResult(Map<String, Object> calculation);
}
