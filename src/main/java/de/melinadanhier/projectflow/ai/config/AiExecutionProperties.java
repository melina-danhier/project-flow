package de.melinadanhier.projectflow.ai.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;

@Setter
@Getter
@Validated
@ConfigurationProperties(prefix = "projectflow.ai")
public class AiExecutionProperties {

    @Min(0)
    @Max(10)
    private int maxAutomaticRetries = 2;

    @NotNull
    private Duration staleWorkflowTimeout = Duration.ofMinutes(5);

    @NotNull
    private Duration retryInitialDelay = Duration.ofSeconds(1);

}
