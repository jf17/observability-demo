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

Единственный endpoint `GET /api/test` выполняет три этапа:

```
GET /api/test
  ├── 1. callExternalApi()   — эмуляция внешнего API, задержка 1200–1500 ms
  └── 2. performCalculation() — эмуляция вычислений, задержка 50–150 ms
```

Каждый этап имеет:
- свой **Micrometer Timer** → метрика в Prometheus
- свой **span** через `@Observed` → трейс в Tempo

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
  "externalCall": {
    "source": "external-api",
    "delayMs": 1380,
    "status": "ok"
  },
  "calculation": {
    "result": 42.7,
    "delayMs": 93,
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
| Actuator (конкретная метрика) | http://localhost:8080/actuator/metrics/api.total | COUNT, TOTAL, MAX |
| Prometheus UI | http://localhost:9090 | Запросы PromQL, графики |
| Grafana Dashboard | http://localhost:3000/d/observability-demo | RPS, latency, ошибки |
| Grafana Explore → Tempo | http://localhost:3000/explore | Waterfall трейсов |

### Метрики в Grafana

Открой http://localhost:3000/d/observability-demo

Доступные панели:
- **RPS** — запросов в секунду
- **Latency p95** — 95-й перцентиль времени ответа
- **Errors** — количество 5xx ошибок
- **Сравнение latency этапов** — `api.total` / `external.call` / `calculation` на одном графике
- **Среднее время этапов** — bargauge с цветовыми порогами (🟢 < 150ms / 🟡 < 250ms / 🔴 > 250ms)

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
http get /api/test     ████████████████████  ~1500ms
  └─ external-call     ██████████████████    ~1380ms
  └─ calculation              ████             ~93ms
```

### Полезные PromQL запросы

```promql
# RPS
rate(api_total_seconds_count[1m])

# p95 latency всего запроса
histogram_quantile(0.95, sum(rate(api_total_seconds_bucket[1m])) by (le))

# Среднее время external call
rate(external_call_seconds_sum[1m]) / rate(external_call_seconds_count[1m])

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
│   │   │   └── DemoService.java            # @Observed, Timer, эмуляция задержек
│   │   └── config/
│   │       ├── ObservabilityConfig.java    # регистрация кастомных Timer-ов
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

# Остановить по отдельности
podman stop prometheus grafana tempo

# Запустить по отдельности
podman start prometheus grafana tempo

# Логи контейнера
podman logs grafana
podman logs tempo
```

---

## 📌 Кастомные метрики

| Метрика | Описание | Prometheus имя |
|---------|----------|----------------|
| `api.total` | Полное время обработки запроса | `api_total_seconds_*` |
| `external.call` | Время вызова внешнего API | `external_call_seconds_*` |
| `calculation` | Время вычислений | `calculation_seconds_*` |

Все метрики публикуют гистограммы (`publishPercentileHistogram`) для расчёта p50/p95/p99.
