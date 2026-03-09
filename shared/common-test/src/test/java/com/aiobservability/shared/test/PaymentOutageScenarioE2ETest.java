package com.aiobservability.shared.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentOutageScenarioE2ETest {
    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_OUTAGE_E2E_TEST", matches = "true")
    void runsPaymentOutageScenarioScriptSuccessfully() throws Exception {
        Path repoRoot = Path.of("..", "..").toAbsolutePath().normalize();
        List<String> command = new ArrayList<>();

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            command.add("powershell");
            command.add("-ExecutionPolicy");
            command.add("Bypass");
            command.add("-File");
            command.add(repoRoot.resolve("scripts").resolve("e2e-payment-outage-test.ps1").toString());
        } else {
            command.add("bash");
            command.add(repoRoot.resolve("scripts").resolve("e2e-payment-outage-test.sh").toString());
        }

        Process process = new ProcessBuilder(command)
                .directory(repoRoot.toFile())
                .redirectErrorStream(true)
                .start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
        }

        boolean completed = process.waitFor(Duration.ofMinutes(8).toMillis(), TimeUnit.MILLISECONDS);
        assertTrue(completed, "Outage E2E script timed out");
        assertEquals(0, process.exitValue(), "Outage E2E script failed:\n" + output);
    }
}
