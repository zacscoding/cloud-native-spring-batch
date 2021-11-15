package com.github.zacscoding.enrich;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RefreshScope
public class EnrichController {
    private int count = 0;
    private final String message;
    private final String instanceId;

    public EnrichController(@Value("${app.message}") String message,
                            @Value("${eureka.instance.instance-id}") String instanceId) {
        logger.info("Instantiate EnrichController with {}", message);
        this.message = message;
        this.instanceId = instanceId;
    }

    @GetMapping("/enrich")
    public String enrich() {
        this.count++;

        if (this.count % 10 == 0) {
            throw new RuntimeException("I screwed up");
        }

        return String.format("[%s] %s_%d", instanceId, message, count);
    }
}


