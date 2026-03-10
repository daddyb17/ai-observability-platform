# API Contracts

Core MVP contracts exposed through `api-gateway`.

Internal endpoint note:

- All `/internal/**` routes now require header `X-Internal-Auth-Token: <INTERNAL_API_TOKEN>`.

## Auth

- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/auth/me`

## Logs

- `GET /api/logs/search`
- `GET /api/logs/{id}`
- `GET /api/logs/trace/{traceId}`
- `POST /internal/logs`
- `POST /internal/logs/batch`

## Incidents

- `GET /api/incidents`
- `GET /api/incidents/{id}`
- `GET /api/incidents/{id}/signals`
- `GET /api/incidents/{id}/timeline`
- `GET /api/incidents/{id}/analysis`
- `PATCH /api/incidents/{id}/status`
- `POST /api/incidents/{id}/analyze`
- `POST /internal/incidents/evaluate`
- `PATCH /internal/incidents/rules`

## Metrics and Traces

- `GET /api/metrics/services/{serviceName}`
- `GET /api/metrics/incidents/{incidentId}`
- `GET /api/traces/{traceId}`
- `GET /api/traces/incident/{incidentId}`
- `POST /internal/metrics/evaluate`
- `POST /internal/traces/summarize`

## Alerts

- `GET /api/alerts`
- `POST /api/alerts/test`
- `POST /internal/alerts/send`
- `POST /internal/alerts/dlq/replay`

## Event Topics

- `logs.raw`
- `metrics.raw`
- `traces.raw`
- `incidents.detected`
- `incidents.updated`
- `ai.analysis.request`
- `ai.analysis.result`
- `alerts.outbound`
