Below is a full implementation blueprint for the AI Observability Platform, written so you can build it step by step and turn it into a strong GitHub portfolio project.

1. Project name

ai-observability-platform

Tagline:

AI-assisted observability and incident diagnosis platform for distributed Java microservices

2. Portfolio goal

This project should prove you can design and build:

production-style Java microservices

event-driven systems with Kafka

observability with logs, metrics, and traces

AI-assisted incident analysis

resilient backend systems

deployable local infrastructure

clean documentation and engineering discipline

3. MVP first, then enhanced version
MVP

Build this first:

3 sample services

structured log ingestion

Elasticsearch search API

Prometheus metrics

Jaeger traces

incident detection by rules

AI summary for incidents

webhook/slack-style alerting

API gateway + JWT auth

Docker Compose local environment

Enhanced version

After MVP:

deduplication and suppression

anomaly baselines

DLQ replay

incident timeline

config-driven rules

Kubernetes deployment

local LLM support

multi-tenant support

4. Monorepo structure

Use a monorepo. It looks good for platform projects.

ai-observability-platform/
¦
+-- README.md
+-- pom.xml
+-- .gitignore
+-- .editorconfig
+-- docker-compose.yml
¦
+-- docs/
¦   +-- architecture/
¦   ¦   +-- system-context.md
¦   ¦   +-- container-diagram.md
¦   ¦   +-- sequence-log-ingestion.md
¦   ¦   +-- sequence-incident-analysis.md
¦   ¦   +-- api-contracts.md
¦   +-- screenshots/
¦   +-- runbooks/
¦   +-- decisions/
¦       +-- adr-001-monorepo.md
¦       +-- adr-002-kafka-topics.md
¦       +-- adr-003-ai-analysis-design.md
¦
+-- infra/
¦   +-- kafka/
¦   +-- postgres/
¦   +-- elasticsearch/
¦   +-- prometheus/
¦   +-- grafana/
¦   +-- jaeger/
¦   +-- redis/
¦   +-- scripts/
¦
+-- shared/
¦   +-- common-models/
¦   +-- common-security/
¦   +-- common-kafka/
¦   +-- common-observability/
¦   +-- common-test/
¦
+-- services/
¦   +-- api-gateway/
¦   +-- auth-service/
¦   +-- log-ingestion-service/
¦   +-- metrics-ingestion-service/
¦   +-- trace-ingestion-service/
¦   +-- incident-detection-service/
¦   +-- ai-analysis-service/
¦   +-- search-query-service/
¦   +-- notification-service/
¦   +-- config-service/                 # optional later
¦   +-- dlq-admin-service/              # optional later
¦
+-- sample-apps/
¦   +-- order-service/
¦   +-- payment-service/
¦   +-- notification-sample-service/
¦
+-- scripts/
¦   +-- start-local.sh
¦   +-- stop-local.sh
¦   +-- simulate-payment-outage.sh
¦   +-- simulate-latency-spike.sh
¦   +-- seed-demo-data.sh
¦
+-- postman/
    +-- ai-observability-platform.postman_collection.json
5. Maven structure

Top-level parent pom.xml.

Modules:

<modules>
    <module>shared/common-models</module>
    <module>shared/common-security</module>
    <module>shared/common-kafka</module>
    <module>shared/common-observability</module>
    <module>shared/common-test</module>

    <module>services/api-gateway</module>
    <module>services/auth-service</module>
    <module>services/log-ingestion-service</module>
    <module>services/metrics-ingestion-service</module>
    <module>services/trace-ingestion-service</module>
    <module>services/incident-detection-service</module>
    <module>services/ai-analysis-service</module>
    <module>services/search-query-service</module>
    <module>services/notification-service</module>

    <module>sample-apps/order-service</module>
    <module>sample-apps/payment-service</module>
    <module>sample-apps/notification-sample-service</module>
</modules>

Use:

Java 21

Spring Boot 3.x

Spring Cloud where needed

Maven wrapper

6. Core architecture
Flow overview
A. Logs

Sample apps emit structured logs ? log-ingestion-service ? Kafka logs.raw ? Elasticsearch ? searchable via search-query-service

B. Metrics

Prometheus scrapes services ? metrics-ingestion-service polls alert conditions or consumes derived signals ? Kafka metrics.raw

C. Traces

Sample apps emit traces via OpenTelemetry ? Jaeger stores traces ? trace-ingestion-service stores summaries/links ? Kafka traces.raw

D. Incident detection

incident-detection-service consumes logs + metrics + traces ? groups related failures ? creates incident ? PostgreSQL ? publishes incidents.detected

E. AI analysis

ai-analysis-service consumes ai.analysis.request ? loads incident signals ? prompts LLM ? writes analysis ? PostgreSQL / Elasticsearch ? publishes ai.analysis.result

F. Notification

notification-service consumes incident events and AI results ? sends webhook/email/mock Slack alerts

7. Service-by-service blueprint
7.1 auth-service
Responsibility

Authentication and token issuance.

Endpoints
POST /api/auth/login
POST /api/auth/refresh
POST /api/auth/logout
GET  /api/auth/me
Data model

users

id

username

password_hash

role

enabled

created_at

Roles

ADMIN

ENGINEER

VIEWER

Implementation

Spring Security

JWT access token

refresh token support

BCrypt password hashing

MVP simplification

Seed users on startup:

admin / admin123

engineer / engineer123

viewer / viewer123

7.2 api-gateway
Responsibility

Single entry point for APIs.

Responsibilities list

validate JWT

route requests

rate limit requests

centralize CORS

correlation headers

Routes
/api/auth/**           -> auth-service
/api/incidents/**      -> incident-detection-service or search-query-service
/api/logs/**           -> search-query-service
/api/metrics/**        -> metrics-ingestion-service
/api/traces/**         -> trace-ingestion-service
/api/alerts/**         -> notification-service
/api/admin/**          -> config-service later
Important filters

Request ID filter

Trace ID passthrough

JWT auth filter

Rate limit filter

Rate limiting

Use Redis-backed rate limiting for:

login

search endpoints

analyze incident endpoint

7.3 log-ingestion-service
Responsibility

Receive and enrich logs, publish to Kafka, index to Elasticsearch.

Endpoints
POST /internal/logs
POST /internal/logs/batch
GET  /actuator/health
Input contract
{
  "serviceName": "payment-service",
  "environment": "dev",
  "timestamp": "2026-03-08T10:15:30Z",
  "level": "ERROR",
  "message": "Database connection timeout",
  "exceptionType": "SQLTimeoutException",
  "stackTrace": "....",
  "traceId": "abc-123",
  "spanId": "span-111",
  "host": "payment-service-1",
  "tags": {
    "region": "local",
    "version": "1.0.0"
  }
}
Responsibilities in detail

validate required fields

add ingestion timestamp

normalize severity

generate event ID

publish to Kafka logs.raw

index in Elasticsearch index logs-*

Elasticsearch index suggestion

Daily or monthly index pattern:

logs-2026-03

Java packages
controller/
service/
validation/
kafka/
elasticsearch/
model/
config/
Key classes

LogIngestionController

LogValidationService

LogEnrichmentService

LogKafkaPublisher

LogIndexService

Failure strategy

invalid schema ? HTTP 400

Kafka failure ? retry, then DLQ

Elasticsearch temporary failure ? retry with backoff

batch partial failures ? return accepted + failed counts

7.4 search-query-service
Responsibility

Unified read/search API for incidents, logs, AI analysis summaries.

Endpoints
GET /api/logs/search
GET /api/logs/{id}
GET /api/logs/trace/{traceId}
GET /api/incidents
GET /api/incidents/{id}
GET /api/incidents/{id}/timeline
GET /api/incidents/{id}/signals
GET /api/incidents/{id}/analysis
Log search filters

serviceName

level

traceId

exceptionType

text

from

to

page

size

sort

Example
GET /api/logs/search?serviceName=payment-service&level=ERROR&from=2026-03-08T10:00:00Z&to=2026-03-08T11:00:00Z&page=0&size=50
Incident search filters

severity

status

affectedService

createdFrom

createdTo

Backing stores

Elasticsearch for logs and searchable analysis text

PostgreSQL for incidents and notification metadata

Jaeger link or trace summaries for traces

7.5 metrics-ingestion-service
Responsibility

Convert Prometheus-based system/application metrics into normalized metric signals and anomalies.

MVP approach

Do not ingest every metric manually. Use Prometheus scraping + service polling.

Strategy

Prometheus scrapes sample apps

metrics-ingestion-service periodically queries Prometheus HTTP API

translates threshold breaches into normalized MetricSignalEvent

publishes to Kafka metrics.raw

Endpoints
GET /api/metrics/services/{serviceName}
GET /api/metrics/incidents/{incidentId}
GET /internal/metrics/rules
POST /internal/metrics/evaluate
Signals to derive

request error rate

p95 latency

CPU usage

JVM heap usage

thread pool saturation

DB pool usage if available

Example normalized signal
{
  "signalId": "sig-2001",
  "serviceName": "payment-service",
  "metricName": "http_server_requests_error_rate",
  "value": 0.28,
  "threshold": 0.10,
  "status": "BREACHED",
  "timestamp": "2026-03-08T10:16:00Z",
  "window": "5m"
}
Detection rules in code first

error rate > 10% over 5m

p95 latency > 2000ms over 5m

JVM heap > 85%

DB connections active > 90%

Later move to DB or config file.

7.6 trace-ingestion-service
Responsibility

Expose trace lookup and produce trace summaries for correlation.

MVP approach

Use Jaeger as trace backend, plus this service for summarization.

Endpoints
GET /api/traces/{traceId}
GET /api/traces/service/{serviceName}
GET /api/traces/incident/{incidentId}
POST /internal/traces/summarize
Data model

trace_summaries

trace_id

root_service

duration_ms

error_flag

span_count

bottleneck_service

bottleneck_span

started_at

Summary generation

Periodically or on-demand:

fetch trace details from Jaeger API

calculate total duration

count spans

detect failed spans

identify slowest span

store summary in PostgreSQL or Elasticsearch

Example summary
{
  "traceId": "abc-123",
  "rootService": "order-service",
  "durationMs": 5210,
  "errorFlag": true,
  "spanCount": 12,
  "bottleneckService": "payment-service",
  "bottleneckSpan": "chargeCard",
  "startedAt": "2026-03-08T10:15:29Z"
}
7.7 incident-detection-service
Responsibility

Core rules engine that creates and updates incidents from signals.

This is the most important service after ingestion.

Endpoints
GET   /api/incidents
GET   /api/incidents/{id}
PATCH /api/incidents/{id}/status
POST  /api/incidents/{id}/analyze
POST  /internal/incidents/evaluate
GET   /internal/incidents/rules
Kafka consumers

Consumes:

logs.raw

metrics.raw

traces.raw

Publishes:

incidents.detected

ai.analysis.request

alerts.outbound

incidents.updated

Incident detection rules
Rule 1: repeated exception burst

If same exceptionType for same service occurs > 20 times in 5 minutes ? incident candidate

Rule 2: high error rate

If http_server_requests_error_rate > 10% for 5 min ? incident candidate

Rule 3: latency spike

If p95 latency > 2 sec for 5 min ? incident candidate

Rule 4: correlated trace failure

If multiple traces in a window fail at same downstream service ? increase severity

Rule 5: cascading failure

If order-service and payment-service both show signal breaches with same trace paths ? escalate

Incident states

OPEN

ACKNOWLEDGED

INVESTIGATING

RESOLVED

Severity levels

LOW

MEDIUM

HIGH

CRITICAL

Severity formula

Start with weights:

repeated errors: +20

latency breach: +20

error rate breach: +30

multiple services affected: +20

failed traces: +15

notification failures: +5

Map:

0–24 LOW

25–49 MEDIUM

50–79 HIGH

80+ CRITICAL

Incident grouping logic

Group signals into same incident if:

same service

similar exception type or metric breach

within 10-minute correlation window

same dominant trace bottleneck if trace exists

Key classes

IncidentRuleEngine

IncidentAggregator

SeverityCalculator

IncidentRepository

IncidentEventPublisher

Persistence

PostgreSQL table incidents

Columns:

id

code

title

description

severity

status

affected_services JSONB

dominant_signal_type

dominant_signal_key

root_trace_id nullable

created_at

updated_at

resolved_at nullable

Additional table incident_signals

id

incident_id

signal_type

signal_key

signal_payload JSONB

observed_at

Trigger AI

When incident created or materially escalated:
publish ai.analysis.request

7.8 ai-analysis-service
Responsibility

Generate structured incident summaries and root-cause hypotheses.

Endpoints
POST /internal/ai/analyze/{incidentId}
GET  /api/incidents/{incidentId}/analysis
GET  /internal/ai/providers
Kafka consumers/publishers

Consumes:

ai.analysis.request

Publishes:

ai.analysis.result

AI provider abstraction

Create interface:

public interface AiClient {
    AiAnalysisResult analyze(IncidentAnalysisPrompt prompt);
}

Implementations:

OpenAiClient

OllamaClient later

MockAiClient for local testing

Preprocessing pipeline

Before prompt:

fetch incident

fetch top related logs

cluster duplicate log messages

fetch breached metric signals

fetch trace summary

build compact evidence model

redact sensitive values

create structured prompt

Prompt input model
{
  "incidentId": "INC-1001",
  "severity": "HIGH",
  "affectedServices": ["payment-service"],
  "topErrors": [
    {
      "message": "Database connection timeout",
      "count": 42
    }
  ],
  "metricBreaches": [
    {
      "metric": "error_rate",
      "value": 0.28,
      "threshold": 0.10
    }
  ],
  "traceSummary": {
    "traceId": "abc-123",
    "bottleneckService": "payment-service",
    "durationMs": 5210
  }
}
Expected AI output contract
{
  "summary": "Payment service is experiencing a database timeout surge causing elevated request failures.",
  "rootCause": "Likely database connection pool exhaustion or upstream DB latency.",
  "confidence": 0.86,
  "recommendedActions": [
    "Inspect database pool metrics",
    "Check recent deployment in payment-service",
    "Scale payment-service temporarily"
  ],
  "evidence": [
    "42 SQLTimeoutException logs in 5 minutes",
    "error rate 28% over 5m",
    "trace bottleneck concentrated in payment-service"
  ]
}
Persistence

Table incident_analysis

id

incident_id

provider

model_name

summary

root_cause

confidence

recommended_actions JSONB

evidence JSONB

raw_response JSONB

created_at

Failure handling

provider timeout ? mark analysis failed, keep incident open

circuit breaker around AI client

max retries 2

fallback to deterministic summary if AI unavailable

Deterministic fallback example

“Payment-service is showing high error rate and repeated database timeout exceptions. Review database availability and connection pool behavior.”

This is important for demos.

7.9 notification-service
Responsibility

Send alerts for incidents and AI analysis.

Endpoints
GET  /api/alerts
POST /api/alerts/test
POST /internal/alerts/send
Channels

MVP:

webhook

mock Slack webhook

email simulation log sink

Kafka consumers

Consumes:

incidents.detected

ai.analysis.result

alerts.outbound

Notification rules

on incident creation

on severity change to HIGH/CRITICAL

on AI analysis completion

on resolution

Table alert_notifications

id

incident_id

channel

payload JSONB

delivery_status

attempt_count

sent_at

error_message nullable

Delivery statuses

PENDING

SENT

FAILED

RETRYING

Example webhook payload
{
  "incidentId": "INC-1001",
  "severity": "HIGH",
  "title": "Payment service database timeout incident",
  "summary": "Payment service is failing due to repeated SQL timeouts.",
  "recommendedActions": [
    "Inspect DB pool saturation",
    "Check DB health"
  ]
}
7.10 sample services

Create 3 sample microservices.

order-service

creates fake orders

calls payment-service

logs business flow

emits traces

exposes metrics

payment-service

processes payment

can simulate DB timeout

can simulate latency spike

emits structured errors

central failure source for demo

notification-sample-service

fake downstream service for order confirmation

lets you show traces across 3 services

Endpoints

order-service

POST /orders
GET  /orders/{id}
POST /orders/load-test

payment-service

POST /payments/charge
POST /payments/simulate/timeout
POST /payments/simulate/latency
POST /payments/simulate/recover

notification-sample-service

POST /notifications/send
Demo flow

order-service ? payment-service ? notification-sample-service

8. Shared libraries blueprint

Only create shared libs for truly shared concerns.

common-models

Contains:

DTOs

event classes

enums

API response wrappers

Examples:

IncidentEvent

MetricSignalEvent

TraceSummaryEvent

ApiErrorResponse

Severity

IncidentStatus

common-kafka

Contains:

Kafka configs

serializers/deserializers

topic constants

dead-letter helpers

common-observability

Contains:

log correlation config

tracing helpers

Micrometer common tags

MDC utilities

common-security

Contains:

JWT token parsing

shared auth models for gateway/resource servers

common-test

Contains:

base integration test utilities

testcontainers setup helpers

JSON fixtures utilities

9. Database schema blueprint

Use PostgreSQL for incident workflow and metadata.

incidents
create table incidents (
    id uuid primary key,
    code varchar(50) unique not null,
    title varchar(255) not null,
    description text,
    severity varchar(20) not null,
    status varchar(30) not null,
    affected_services jsonb not null,
    dominant_signal_type varchar(50),
    dominant_signal_key varchar(255),
    root_trace_id varchar(255),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    resolved_at timestamptz
);
incident_signals
create table incident_signals (
    id uuid primary key,
    incident_id uuid not null references incidents(id),
    signal_type varchar(50) not null,
    signal_key varchar(255) not null,
    signal_payload jsonb not null,
    observed_at timestamptz not null
);
incident_analysis
create table incident_analysis (
    id uuid primary key,
    incident_id uuid not null references incidents(id),
    provider varchar(50) not null,
    model_name varchar(100) not null,
    summary text,
    root_cause text,
    confidence numeric(4,3),
    recommended_actions jsonb,
    evidence jsonb,
    raw_response jsonb,
    created_at timestamptz not null
);
alert_notifications
create table alert_notifications (
    id uuid primary key,
    incident_id uuid not null references incidents(id),
    channel varchar(30) not null,
    payload jsonb not null,
    delivery_status varchar(20) not null,
    attempt_count int not null default 0,
    sent_at timestamptz,
    error_message text
);
users
create table users (
    id uuid primary key,
    username varchar(100) unique not null,
    password_hash varchar(255) not null,
    role varchar(30) not null,
    enabled boolean not null default true,
    created_at timestamptz not null
);
trace_summaries
create table trace_summaries (
    trace_id varchar(255) primary key,
    root_service varchar(100) not null,
    duration_ms bigint not null,
    error_flag boolean not null,
    span_count int not null,
    bottleneck_service varchar(100),
    bottleneck_span varchar(255),
    started_at timestamptz not null
);

10. Elasticsearch design
Indices

logs-yyyy-mm

incident-analysis-yyyy-mm optional

incident-events-yyyy-mm optional

Log mapping fields

eventId keyword

timestamp date

serviceName keyword

environment keyword

level keyword

message text + keyword

exceptionType keyword

traceId keyword

spanId keyword

host keyword

tags.* keyword

Important

Store structured logs, not raw text only.

11. Kafka topic blueprint
Topics
logs.raw
metrics.raw
traces.raw
incidents.detected
incidents.updated
ai.analysis.request
ai.analysis.result
alerts.outbound
deadletter.logs
deadletter.metrics
deadletter.traces
deadletter.incidents
deadletter.ai
deadletter.alerts
Suggested partitions

For local demo:

logs.raw: 3

metrics.raw: 2

traces.raw: 2

incidents.detected: 2

ai.analysis.request: 1

ai.analysis.result: 1

alerts.outbound: 1

Topic key strategy

logs.raw ? serviceName or traceId

metrics.raw ? serviceName

traces.raw ? traceId

incidents.detected ? incidentId

ai.analysis.request ? incidentId

alerts.outbound ? incidentId

This helps ordering for correlated events.

12. Event contracts

Use versioned event envelopes.

Event envelope
{
  "eventId": "evt-123",
  "eventType": "LOG_RECEIVED",
  "eventVersion": "1.0",
  "occurredAt": "2026-03-08T10:15:30Z",
  "source": "log-ingestion-service",
  "payload": {}
}
Log event payload
{
  "logId": "log-123",
  "serviceName": "payment-service",
  "environment": "dev",
  "timestamp": "2026-03-08T10:15:30Z",
  "level": "ERROR",
  "message": "Database connection timeout",
  "exceptionType": "SQLTimeoutException",
  "traceId": "abc-123",
  "spanId": "span-111",
  "host": "payment-service-1",
  "tags": {
    "version": "1.0.0"
  }
}
Metric signal event
{
  "signalId": "sig-001",
  "serviceName": "payment-service",
  "metricName": "error_rate",
  "value": 0.28,
  "threshold": 0.10,
  "status": "BREACHED",
  "window": "5m",
  "timestamp": "2026-03-08T10:16:00Z"
}
Trace summary event
{
  "traceId": "abc-123",
  "rootService": "order-service",
  "durationMs": 5210,
  "errorFlag": true,
  "spanCount": 12,
  "bottleneckService": "payment-service",
  "bottleneckSpan": "chargeCard",
  "startedAt": "2026-03-08T10:15:29Z"
}
Incident detected event
{
  "incidentId": "INC-1001",
  "title": "Payment service timeout burst",
  "severity": "HIGH",
  "status": "OPEN",
  "affectedServices": ["payment-service"],
  "dominantSignalType": "LOG_EXCEPTION_BURST",
  "detectedAt": "2026-03-08T10:16:10Z"
}
AI analysis result event
{
  "incidentId": "INC-1001",
  "summary": "Payment-service is failing due to repeated SQL timeout exceptions.",
  "rootCause": "Likely DB latency or exhausted connection pool.",
  "confidence": 0.86,
  "recommendedActions": [
    "Check DB connectivity",
    "Inspect Hikari pool usage"
  ],
  "generatedAt": "2026-03-08T10:16:25Z"
}

13. REST API blueprint
Auth
POST /api/auth/login
POST /api/auth/refresh
GET  /api/auth/me
Incidents
GET   /api/incidents
GET   /api/incidents/{id}
GET   /api/incidents/{id}/signals
GET   /api/incidents/{id}/timeline
GET   /api/incidents/{id}/analysis
PATCH /api/incidents/{id}/status
POST  /api/incidents/{id}/analyze
Logs
GET /api/logs/search
GET /api/logs/{id}
GET /api/logs/trace/{traceId}
Metrics
GET /api/metrics/services/{serviceName}
GET /api/metrics/incidents/{incidentId}
Traces
GET /api/traces/{traceId}
GET /api/traces/incident/{incidentId}
Alerts
GET  /api/alerts
POST /api/alerts/test
Internal
POST /internal/logs
POST /internal/logs/batch
POST /internal/incidents/evaluate
POST /internal/ai/analyze/{incidentId}
POST /internal/traces/summarize
POST /internal/metrics/evaluate

14. Security blueprint
Auth model

auth-service issues JWT

gateway validates JWT

downstream services can trust forwarded auth headers or validate JWT directly

Role policy
VIEWER

can read incidents/logs/metrics/traces

ENGINEER

can trigger AI analysis

can acknowledge/update incidents

ADMIN

can change thresholds

can manage configs

can view internal/admin endpoints

Security tasks

secure actuator except health/info

use environment variables for secrets

validate request payloads

add simple audit log for login and incident updates

15. Observability blueprint for the platform itself

Every service must expose:

/actuator/health

/actuator/prometheus

structured JSON logs

trace propagation headers

correlation IDs

Metrics to expose

request count

request latency

error count

Kafka consumer lag if possible

Kafka publish success/failure

AI request time

notification success/failure

incident creation count

Log format example
{
  "timestamp": "2026-03-08T10:15:30.120Z",
  "level": "ERROR",
  "service": "payment-service",
  "traceId": "abc-123",
  "spanId": "span-111",
  "message": "Database connection timeout",
  "exceptionType": "SQLTimeoutException"
}

16. Docker Compose blueprint

Your docker-compose.yml should include:

kafka

kafka-ui optional

postgres

redis

elasticsearch

kibana optional

prometheus

grafana

jaeger

all services

sample apps

Suggested startup order

postgres

kafka

elasticsearch

redis

prometheus

grafana

jaeger

auth-service

ingestion/search/detection services

sample apps

gateway

Good ports

gateway: 8080

auth-service: 8081

log-ingestion: 8082

search-query: 8083

incident-detection: 8084

ai-analysis: 8085

notification: 8086

metrics-ingestion: 8087

trace-ingestion: 8088

order-service: 8091

payment-service: 8092

notification-sample: 8093

grafana: 3000

prometheus: 9090

jaeger: 16686

elasticsearch: 9200

postgres: 5432

redis: 6379

17. Configuration blueprint

Use per-service application.yml plus profile overrides.

Shared config sections

server port

datasource

kafka bootstrap servers

JWT secret

elasticsearch URL

redis URL

otel exporter endpoint

AI provider config

AI config example
app:
  ai:
    provider: mock
    timeout-seconds: 20
    max-retries: 2
    openai:
      model: gpt-4o-mini
      api-key: ${OPENAI_API_KEY:}
    ollama:
      model: llama3
      base-url: http://ollama:11434

For local public GitHub repo, default to mock provider.

18. Development order blueprint

Use this exact sequence.

Phase 1: repo foundation

initialize monorepo

create parent pom

create shared modules

add editorconfig, spotless/checkstyle

create README skeleton

Phase 2: infra

add docker-compose with postgres, kafka, elasticsearch, prometheus, grafana, jaeger, redis

verify all infra boots

add scripts start-local.sh and stop-local.sh

Phase 3: sample apps

build order-service

build payment-service

build notification-sample-service

add OpenTelemetry and Micrometer

emit structured logs and traces

Phase 4: auth + gateway

build auth-service

build gateway

protect APIs with JWT

Phase 5: logs

build log-ingestion-service

publish to Kafka

index into Elasticsearch

build search-query-service log search endpoints

Phase 6: metrics and traces

configure Prometheus scraping

build metrics-ingestion-service

build trace-ingestion-service

store trace summaries

Phase 7: incidents

build incident-detection-service

implement rules engine

persist incidents and signals

publish incidents.detected

Phase 8: AI

build ai-analysis-service

add mock provider

add real provider abstraction

save analysis results

publish ai.analysis.result

Phase 9: notifications

build notification-service

send webhook/mock Slack alerts

persist alert history

Phase 10: polish

add end-to-end simulation scripts

add integration tests with Testcontainers

add GitHub Actions

add architecture diagrams and screenshots

19. Testing blueprint
Unit tests

Target:

severity calculation

rule matching

log normalization

metric threshold checks

AI prompt builder

notification payload builder

JWT utils

Integration tests

Use Testcontainers for:

PostgreSQL

Kafka

Elasticsearch

Redis

Critical integration cases

log ingestion indexes document successfully

incident created from repeated errors

AI analysis saved after request event

notification event results in persisted alert history

End-to-end tests

Scenario:

payment-service enters timeout mode

create order load

logs spike

metrics breach

trace bottleneck appears

incident created

AI analysis generated

alert sent

This scenario should be reproducible with one script.

20. Demo scenario blueprint
Primary showcase demo

Payment outage due to DB timeout

Steps

run full stack

login and get JWT

start order load

trigger payment timeout mode

wait for logs/metrics/traces

search logs for payment-service errors

view generated incident

trigger or wait for AI analysis

show webhook alert

recover payment-service

mark incident resolved

Script plan

scripts/simulate-payment-outage.sh

enable timeout mode on payment-service

send batch order requests

wait 30–60 seconds

optionally call AI analyze endpoint

21. Readme blueprint

Your README.md should include:

Sections

project overview

why this project matters

architecture diagram

service responsibilities

tech stack

local setup

how to run

demo walkthrough

sample API calls

screenshots

testing

future improvements

Include this section

Senior engineering concepts demonstrated

event-driven design

distributed tracing

structured logging

incident correlation

resilience patterns

AI-assisted operational diagnosis

This helps recruiters instantly understand your level.

22. GitHub presentation strategy

Make the repo visually strong.

Add these assets

architecture PNG

sequence diagram PNG

Grafana dashboard screenshot

Jaeger trace screenshot

incident API response screenshot

AI summary example screenshot

Add badges

build passing

tests passing

Java version

Spring Boot version

Dockerized

23. Milestone plan
Milestone 1: Platform skeleton

Deliver:

monorepo

docker-compose

shared libs

sample apps emitting logs and traces

Milestone 2: Log pipeline

Deliver:

log ingestion

Kafka logs topic

Elasticsearch indexing

log search API

Milestone 3: Metrics and traces

Deliver:

Prometheus

Grafana dashboards

trace summaries

trace lookup API

Milestone 4: Incident engine

Deliver:

rules engine

incident persistence

incident APIs

incident grouping

Milestone 5: AI analysis

Deliver:

AI analysis service

summary and root cause

confidence score

recommendation output

Milestone 6: Alerts and polish

Deliver:

notification-service

JWT auth

gateway

end-to-end demo scripts

tests

docs

24. Recommended class design for incident-detection-service
incident-detection-service/
+-- controller/
¦   +-- IncidentController.java
+-- service/
¦   +-- IncidentService.java
¦   +-- IncidentRuleEngine.java
¦   +-- IncidentAggregationService.java
¦   +-- SeverityCalculator.java
¦   +-- IncidentAnalysisTriggerService.java
+-- consumer/
¦   +-- LogEventConsumer.java
¦   +-- MetricSignalConsumer.java
¦   +-- TraceSummaryConsumer.java
+-- repository/
¦   +-- IncidentRepository.java
¦   +-- IncidentSignalRepository.java
¦   +-- IncidentAnalysisRepository.java
+-- model/
¦   +-- IncidentEntity.java
¦   +-- IncidentSignalEntity.java
¦   +-- IncidentStatus.java
+-- rules/
¦   +-- ErrorBurstRule.java
¦   +-- ErrorRateRule.java
¦   +-- LatencySpikeRule.java
¦   +-- CascadingFailureRule.java
+-- config/
    +-- KafkaConsumerConfig.java

Use the same discipline for each service.

25. Recommended class design for ai-analysis-service
ai-analysis-service/
+-- controller/
¦   +-- AiAnalysisController.java
+-- service/
¦   +-- AiAnalysisService.java
¦   +-- IncidentEvidenceAssembler.java
¦   +-- PromptBuilder.java
¦   +-- ResponseParser.java
¦   +-- RedactionService.java
+-- client/
¦   +-- AiClient.java
¦   +-- MockAiClient.java
¦   +-- OpenAiClient.java
¦   +-- OllamaClient.java
+-- consumer/
¦   +-- AiAnalysisRequestConsumer.java
+-- repository/
¦   +-- IncidentAnalysisRepository.java
+-- model/
¦   +-- AiAnalysisResult.java
¦   +-- PromptEvidence.java
¦   +-- RecommendedAction.java
+-- config/
    +-- AiProviderConfig.java
26. Resilience blueprint

Use Resilience4j for:

AI provider calls

Elasticsearch indexing

webhook notifications

Patterns

retry

timeout

circuit breaker

rate limiter for AI calls

Example use

AI provider call:

timeout 15s

retry 2 times

open circuit after 50% failure in rolling window

27. DLQ blueprint

At least implement this for logs and AI.

When to send to DLQ

malformed event

unrecoverable deserialization

repeated downstream failure after retries

DLQ payload

Include:

original topic

partition

offset

error type

error message

original payload

failedAt

Later you can build dlq-admin-service with:

list failed records

replay record

purge records

That is a very nice senior-level enhancement.

28. Config-driven rules blueprint

After MVP, move hardcoded thresholds into DB or YAML.

Example config
app:
  incident-rules:
    error-burst:
      count-threshold: 20
      window-minutes: 5
    error-rate:
      threshold: 0.10
      window-minutes: 5
    latency-spike:
      p95-threshold-ms: 2000
      window-minutes: 5

Then build RuleProperties config class.

29. Roadmap after MVP

Once the base platform works, add one or two of these:

Best enhancements

incident timeline page endpoint

deduplication/suppression window

replayable DLQ

model/provider switch between Mock/OpenAI/Ollama

Kubernetes manifests

Helm chart

synthetic traffic generator

per-service dashboards

deployment event correlation

The strongest two for portfolio value:

DLQ replay

config-driven rules

Kubernetes deployment

local LLM option

30. Definition of done for each service
log-ingestion-service

Done when:

accepts batch and single logs

publishes to Kafka

indexes to Elasticsearch

handles invalid records safely

search-query-service

Done when:

searches logs with filters

fetches incidents with related analysis

supports pagination and sorting

metrics-ingestion-service

Done when:

derives normalized metric breach events

publishes metrics.raw

exposes metric lookup APIs

trace-ingestion-service

Done when:

fetches/stores trace summaries

correlates trace by incident or traceId

incident-detection-service

Done when:

creates incidents from logs/metrics/traces

updates severity and status

publishes incident events

ai-analysis-service

Done when:

assembles evidence

generates structured summary

persists analysis

falls back safely on failure

notification-service

Done when:

sends webhook alerts

stores delivery history

retries failures

api-gateway/auth-service

Done when:

JWT works

roles enforced

all public APIs routed through gateway

31. Best first week execution checklist

Here is the practical starting checklist.

Day 1

create monorepo

parent pom

README skeleton

docker-compose skeleton

Day 2

bring up postgres, kafka, elasticsearch, prometheus, grafana, jaeger

verify all dashboards are reachable

Day 3

create order-service and payment-service

add Micrometer and OpenTelemetry

emit structured logs

Day 4

create log-ingestion-service

receive logs and push to Kafka

Day 5

index logs in Elasticsearch

verify searchable data

Day 6

build search-query-service /api/logs/search

Day 7

polish docs and add screenshot of working log search

That gives visible progress immediately.

32. Strong recommendation on scope control

Do not build these in the first pass:

full custom frontend

complex ML anomaly detection

multi-region deployment

true tenant isolation

massive schema registry setup

too many shared libraries

Build the platform backbone first. That is what recruiters will care about most.

33. Final recommended MVP service count

For a manageable but impressive version, build exactly these first:

auth-service

api-gateway

log-ingestion-service

search-query-service

metrics-ingestion-service

trace-ingestion-service

incident-detection-service

ai-analysis-service

notification-service

order-service

payment-service

notification-sample-service

That is enough to look serious without becoming unfinishable.

34. What to build next from here

The cleanest next step is to turn this blueprint into actual project planning artifacts:

folder-by-folder task breakdown

database DDL scripts

Kafka event Java DTOs

API contracts/OpenAPI draft

Docker Compose starter file

phase-wise weekly implementation checklist

I recommend doing the next step as:
“generate the exact folder structure, starter pom files, and phase-1 tasks for week 1.”
