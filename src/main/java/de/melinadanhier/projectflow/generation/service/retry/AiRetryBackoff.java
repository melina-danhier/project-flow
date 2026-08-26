package de.melinadanhier.projectflow.generation.service.retry;

import de.melinadanhier.projectflow.ai.config.AiExecutionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiRetryBackoff {

    private final AiExecutionProperties properties;

    public void waitBeforeRetry(int retryNumber) throws InterruptedException {
        long initialDelayMillis = properties.getRetryInitialDelay().toMillis();
        long multiplier = 1L << Math.min(20, Math.max(0, retryNumber - 1));
        long delayMillis = Math.multiplyExact(initialDelayMillis, multiplier);
        Thread.sleep(delayMillis);
    }
}
