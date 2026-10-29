package dev.agentforge.prreviewer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class PrReviewerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrReviewerApplication.class, args);
    }
}
