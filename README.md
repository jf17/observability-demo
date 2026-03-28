# 🔭 Observability Demo

> Demo-проект для изучения best practices observability в Java/Spring Boot приложениях.

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.4-brightgreen?logo=springboot)
![Micrometer](https://img.shields.io/badge/Micrometer-tracing-blue)
![OpenTelemetry](https://img.shields.io/badge/OpenTelemetry-OTLP-blueviolet?logo=opentelemetry)
![Prometheus](https://img.shields.io/badge/Prometheus-scrape-red?logo=prometheus)
![Grafana](https://img.shields.io/badge/Grafana-dashboard-orange?logo=grafana)

---

## 📐 Архитектура

```
┌─────────────────────────┐
│   Spring Boot App :8080  │
│                          │
│  Micrometer Timers       │──── scrape ──────▶ Prometheus :9090
│  @Observed (OTel bridge) │──── OTLP HTTP ───▶ Tempo :4318
└─────────────────────────┘
                                                      │
                                               Grafana :3000
                                            (метрики + трейсы)
```

| Слой | Технология | Назначение |
|------|-----------|------------|
| Метрики | Micrometer + Prometheus | Агрегированные данные: RPS, latency p95, ошибки |
| Трейсы | OpenTelemetry + Tempo | Детальный путь каждого запроса (waterfall) |
| Логи | Logback + MDC | `traceId`/`spanId` в каждой строке лога |

---

## 🧱 Стек

- **Java 21** / **Spring Boot 3.2.4**
- **Gradle** (Groovy DSL)
- **Micrometer** — метрики + трейсинг bridge
- **OpenTelemetry** — экспорт трейсов по OTLP
- **Prometheus** — хранение метрик
- **Grafana** — визуализация
- **Tempo** — хранение трейсов
- **Podman** — контейнеры (вместо Docker)

---

## 📦 Функциональность

Единственный endpoint `GET /api/test` выполняет четыре последовательных этапа:

```
GET /api/test
  ├── 1. callFirstApi()    — эмуляция первого внешнего API,  задержка 1200–1500 ms
  ├── 2. callSecondApi()   — эмуляция второго внешнего API,  задержка 800–1200 ms
  ├── 3. performCalculation() — вычисление суммы и среднего из двух ответов, задержка 50–150 ms
  └── 4. sendResult()      — отправка результата в третий API, задержка 300–600 ms
```

Каждый этап имеет:
- свой **Micrometer Timer** → метрика в Prometheus
- свой **span** через `@Observed` → трейс в Tempo

### Почему сервисы разбиты на отдельные бины?

`@Observed` работает через Spring AOP proxy. Если вызывать методы внутри одного бина (self-invocation), proxy не перехватывает вызов и span не создаётся. Поэтому каждый логический слой вынесен в отдельный бин, вызываемый через интерфейс:

| Интерфейс | Реализация | Назначение |
|-----------|-----------|------------|
| `BusinessService` | `DemoService` | Оркестратор — координирует все этапы |
| `ExternalApiClientService` | `ExternalApiClient` | Вызовы первого, второго и третьего API |
| `CalculationServiceApi` | `CalculationService` | Вычисления на основе двух ответов |

---

## 🚀 Быстрый старт

### Требования

- Java 21 (`/usr/lib/jvm/java-1.21.0-openjdk-amd64`)
- Podman + podman-compose

```bash
pip3 install podman-compose
```

### 1. Запустить инфраструктуру

```bash
podman-compose -f infra/podman-compose.yml up -d
```

### 2. Запустить приложение

```bash
./gradlew bootRun
```

> При первом запуске Gradle скачает дистрибутив (~130 MB). Последующие запуски быстрее.

### 3. Сделать тестовые запросы

```bash
# Один запрос
curl http://localhost:8080/api/test

# Нагрузка для генерации метрик
for i in $(seq 1 20); do curl -s http://localhost:8080/api/test; done
```

<details>
<summary>Пример ответа</summary>

```json
{
  "timestamp": "2026-03-28T18:00:00Z",
  "status": "success",
  "firstApi": {
    "source": "first-api",
    "value": 73.4,
    "delayMs": 1380,
    "status": "ok"
  },
  "secondApi": {
    "source": "second-api",
    "value": 45.1,
    "delayMs": 1020,
    "status": "ok"
  },
  "calculation": {
    "v1": 73.4,
    "v2": 45.1,
    "sum": 118.5,
    "avg": 59.25,
    "delayMs": 93,
    "status": "ok"
  },
  "sendResult": {
    "destination": "third-api",
    "delayMs": 450,
    "status": "ok"
  }
}
```

</details>

---

## 📊 Где смотреть данные

| Интерфейс | URL | Что смотреть |
|-----------|-----|-------------|
| Actuator (метрики raw) | http://localhost:8080/actuator/prometheus | Сырые метрики Prometheus |
| Prometheus UI | http://localhost:9090 | Запросы PromQL, графики |
| Grafana Dashboard | http://localhost:3000/d/observability-demo | RPS, latency, ошибки |
| Grafana Explore → Tempo | http://localhost:3000/explore | Waterfall трейсов |

### Метрики в Grafana

Открой http://localhost:3000/d/observability-demo

Доступные панели:
- **RPS** — запросов в секунду
- **Latency p50 / p95 — api.total** — перцентили полного времени ответа
- **Errors** — количество 5xx ошибок
- **Traces** — поиск трейсов в Tempo
- **Latency p95 — все этапы** — сравнение `api.total`, `first.api.call`, `second.api.call`, `calculation`, `send.result` на одном графике
- **Среднее время этапов** — bargauge с цветовыми порогами (🟢 < 500ms / 🟡 < 1000ms / 🔴 > 1000ms)

### Трейсы в Grafana Tempo

1. Открыть http://localhost:3000/explore
2. Datasource → **Tempo**
3. Ввести TraceQL запрос:
```
{resource.service.name="observability-demo"}
```
4. Кликнуть на трейс `http get /api/test`

Результат — waterfall диаграмма:
```
http get /api/test  [════════════════════════════════════] ~3400ms
  first-api-call    [══════════════════]                   ~1380ms
  second-api-call                      [════════════]      ~1000ms
  calculation                                       [══]     ~93ms
  send-result                                          [═══] ~450ms
```

### Полезные PromQL запросы

```promql
# RPS
rate(api_total_seconds_count[1m])

# p95 latency полного запроса
histogram_quantile(0.95, sum(rate(api_total_seconds_bucket[1m])) by (le))

# Среднее время первого API
rate(first_api_call_seconds_sum[1m]) / rate(first_api_call_seconds_count[1m])

# Среднее время второго API
rate(second_api_call_seconds_sum[1m]) / rate(second_api_call_seconds_count[1m])

# Среднее время вычислений
rate(calculation_seconds_sum[1m]) / rate(calculation_seconds_count[1m])

# Среднее время отправки результата
rate(send_result_seconds_sum[1m]) / rate(send_result_seconds_count[1m])

# Ошибки 5xx
rate(http_server_requests_seconds_count{status=~"5.."}[1m])
```

---

## 📁 Структура проекта

```
observability-demo/
├── build.gradle                            # зависимости (Groovy DSL)
├── settings.gradle
├── gradle.properties                       # Java 21 для Gradle daemon
├── gradlew
├── gradle/wrapper/
│   ├── gradle-wrapper.jar
│   └── gradle-wrapper.properties           # Gradle 8.13
│
├── src/main/
│   ├── java/com/demo/observability/
│   │   ├── ObservabilityDemoApplication.java
│   │   ├── controller/
│   │   │   └── TestController.java         # GET /api/test, Timer.record()
│   │   ├── service/
│   │   │   ├── BusinessService.java        # интерфейс оркестратора
│   │   │   ├── DemoService.java            # реализация: координирует все этапы
│   │   │   ├── ExternalApiClientService.java  # интерфейс внешних вызовов
│   │   │   ├── ExternalApiClient.java      # реализация: first, second, send API
│   │   │   ├── CalculationServiceApi.java  # интерфейс вычислений
│   │   │   └── CalculationService.java     # реализация: sum + avg из двух ответов
│   │   └── config/
│   │       ├── ObservabilityConfig.java    # регистрация Timer-ов
│   │       └── TracingConfig.java          # ObservedAspect для @Observed AOP
│   └── resources/
│       └── application.yml                 # метрики, трейсинг, OTLP endpoint
│
└── infra/
    ├── podman-compose.yml                  # Prometheus + Grafana + Tempo
    ├── prometheus/
    │   └── prometheus.yml                  # scrape config
    ├── tempo/
    │   └── tempo.yml                       # OTLP receiver, local storage
    └── grafana/provisioning/
        ├── datasources/
        │   └── datasources.yml             # Prometheus + Tempo datasources
        └── dashboards/
            ├── dashboards.yml
            └── observability.json          # готовый dashboard
```

---

## 🔧 Управление контейнерами

```bash
# Запустить все
podman-compose -f infra/podman-compose.yml up -d

# Остановить все
podman-compose -f infra/podman-compose.yml down

# Логи контейнера
podman logs grafana
podman logs tempo
podman logs prometheus
```

---

## 📌 Кастомные метрики

| Метрика | Описание | Prometheus имя |
|---------|----------|----------------|
| `api.total` | Полное время обработки запроса | `api_total_seconds_*` |
| `first.api.call` | Время вызова первого внешнего API | `first_api_call_seconds_*` |
| `second.api.call` | Время вызова второго внешнего API | `second_api_call_seconds_*` |
| `calculation` | Время вычислений (sum + avg) | `calculation_seconds_*` |
| `send.result` | Время отправки результата в третий API | `send_result_seconds_*` |

Все метрики публикуют гистограммы (`publishPercentileHistogram`) для расчёта p50/p95/p99.
