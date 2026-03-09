# Outage E2E Test Runbook

Use this runbook to execute a stronger end-to-end outage scenario check.

## Scenario

- Trigger payment timeout mode.
- Generate order load.
- Verify incident creation.
- Verify AI analysis generated.
- Verify alert history persisted.
- Recover payment-service.

## Scripts

- Bash: `scripts/e2e-payment-outage-test.sh`
- PowerShell: `scripts/e2e-payment-outage-test.ps1`

## Manual Execution

1. Start infra:
   - `./scripts/start-local.sh`
2. Start services:
   - `./scripts/start-demo-services.sh`
3. Run E2E test:
   - `./scripts/e2e-payment-outage-test.sh`
4. Stop services:
   - `./scripts/stop-demo-services.sh`
5. Stop infra:
   - `./scripts/stop-local.sh`

## Optional JUnit Wrapper

`shared/common-test` includes `PaymentOutageScenarioE2ETest` gated by:

```bash
export RUN_OUTAGE_E2E_TEST=true
```

Then run:

```bash
./mvnw -pl shared/common-test -Dtest=PaymentOutageScenarioE2ETest test
```
