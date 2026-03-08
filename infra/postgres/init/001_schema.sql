create extension if not exists "pgcrypto";

create table if not exists incidents (
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

create table if not exists incident_signals (
    id uuid primary key,
    incident_id uuid not null references incidents(id),
    signal_type varchar(50) not null,
    signal_key varchar(255) not null,
    signal_payload jsonb not null,
    observed_at timestamptz not null
);

create table if not exists incident_analysis (
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

create table if not exists alert_notifications (
    id uuid primary key,
    incident_id uuid not null references incidents(id),
    channel varchar(30) not null,
    payload jsonb not null,
    delivery_status varchar(20) not null,
    attempt_count int not null default 0,
    sent_at timestamptz,
    error_message text
);

create table if not exists users (
    id uuid primary key,
    username varchar(100) unique not null,
    password_hash varchar(255) not null,
    role varchar(30) not null,
    enabled boolean not null default true,
    created_at timestamptz not null
);

create table if not exists trace_summaries (
    trace_id varchar(255) primary key,
    root_service varchar(100) not null,
    duration_ms bigint not null,
    error_flag boolean not null,
    span_count int not null,
    bottleneck_service varchar(100),
    bottleneck_span varchar(255),
    started_at timestamptz not null
);
