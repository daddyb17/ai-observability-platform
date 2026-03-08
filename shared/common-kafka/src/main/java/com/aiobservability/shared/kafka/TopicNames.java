package com.aiobservability.shared.kafka;

public final class TopicNames {
    public static final String LOGS_RAW = "logs.raw";
    public static final String METRICS_RAW = "metrics.raw";
    public static final String TRACES_RAW = "traces.raw";
    public static final String INCIDENTS_DETECTED = "incidents.detected";
    public static final String INCIDENTS_UPDATED = "incidents.updated";
    public static final String AI_ANALYSIS_REQUEST = "ai.analysis.request";
    public static final String AI_ANALYSIS_RESULT = "ai.analysis.result";
    public static final String ALERTS_OUTBOUND = "alerts.outbound";

    public static final String DEADLETTER_LOGS = "deadletter.logs";
    public static final String DEADLETTER_METRICS = "deadletter.metrics";
    public static final String DEADLETTER_TRACES = "deadletter.traces";
    public static final String DEADLETTER_INCIDENTS = "deadletter.incidents";
    public static final String DEADLETTER_AI = "deadletter.ai";
    public static final String DEADLETTER_ALERTS = "deadletter.alerts";

    private TopicNames() {
    }
}
