# AI Provider Switch Demo Runbook

This runbook demonstrates switching AI providers for `ai-analysis-service` during local demos.

## Prerequisites

- Platform infra is running (`scripts/start-local.sh` or `.ps1`).
- Demo services are running (`scripts/start-demo-services.sh` or `.ps1`).

## 1) Mock Provider (Default)

Use when you want deterministic, offline-safe demo behavior.

```bash
export AI_PROVIDER=mock
```

PowerShell:

```powershell
$env:AI_PROVIDER = "mock"
```

Restart `ai-analysis-service` after changing provider env vars.

## 2) OpenAI Provider

Use when you want model-backed summaries.

```bash
export AI_PROVIDER=openai
export OPENAI_API_KEY=your_key_here
export OPENAI_MODEL=gpt-4o-mini
```

PowerShell:

```powershell
$env:AI_PROVIDER = "openai"
$env:OPENAI_API_KEY = "your_key_here"
$env:OPENAI_MODEL = "gpt-4o-mini"
```

Optional:

- `OPENAI_BASE_URL` for proxy/gateway routing.

## 3) Ollama Provider

Use when running a local model host.

```bash
export AI_PROVIDER=ollama
export OLLAMA_MODEL=llama3
export OLLAMA_BASE_URL=http://localhost:11434
```

PowerShell:

```powershell
$env:AI_PROVIDER = "ollama"
$env:OLLAMA_MODEL = "llama3"
$env:OLLAMA_BASE_URL = "http://localhost:11434"
```

Note: current implementation keeps Ollama as a planned provider; switch for wiring demos only until full client support is added.

## 4) Verify Active Provider

```bash
curl http://localhost:8085/internal/ai/providers
```

PowerShell:

```powershell
Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:8085/internal/ai/providers" | Select-Object -ExpandProperty Content
```

Confirm one provider has `"selected": true`.
