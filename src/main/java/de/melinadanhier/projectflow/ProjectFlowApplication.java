package de.melinadanhier.projectflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ProjectFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProjectFlowApplication.class, args);
    }

}
