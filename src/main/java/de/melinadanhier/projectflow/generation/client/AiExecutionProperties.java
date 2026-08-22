package de.melinadanhier.projectflow.generation.client;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "projectflow.ai")
public class AiExecutionProperties {

    @Min(0)
    @Max(10)
    private int maxAutomaticRetries = 2;
    @NotNull
    private Duration staleWorkflowTimeout = Duration.ofMinutes(5);

    public int getMaxAutomaticRetries() {
        return maxAutomaticRetries;
    }

    public void setMaxAutomaticRetries(int maxAutomaticRetries) {
        this.maxAutomaticRetries = maxAutomaticRetries;
    }

    public Duration getStaleWorkflowTimeout() {
        return staleWorkflowTimeout;
    }

    public void setStaleWorkflowTimeout(Duration staleWorkflowTimeout) {
        this.staleWorkflowTimeout = staleWorkflowTimeout;
    }
}
