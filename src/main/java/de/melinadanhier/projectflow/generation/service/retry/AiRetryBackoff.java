package de.melinadanhier.projectflow.generation.service.retry;

import org.springframework.stereotype.Component;

@Component
public class AiRetryBackoff {

    private static final long INITIAL_DELAY_MILLIS = 250;

    public void waitBeforeRetry(int retryNumber) throws InterruptedException {
        long delayMillis = INITIAL_DELAY_MILLIS * (1L << Math.max(0, retryNumber - 1));
        Thread.sleep(delayMillis);
    }
}
