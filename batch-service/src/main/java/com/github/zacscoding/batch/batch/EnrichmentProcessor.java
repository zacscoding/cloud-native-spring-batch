package com.github.zacscoding.batch.batch;

import java.util.concurrent.TimeUnit;

import javax.validation.constraints.NotNull;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.CircuitBreaker;
import org.springframework.retry.annotation.Recover;
import org.springframework.web.client.RestTemplate;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class EnrichmentProcessor implements ItemProcessor<Foo, Foo> {

    private final RestTemplate restTemplate;

    @Recover
    public Foo fallback(Foo foo) {
        foo.setMessage("error-fallback");
        try {
            TimeUnit.MILLISECONDS.sleep(100L);
        } catch (Exception ignored) {
        }
        return foo;
    }

    @CircuitBreaker(maxAttempts = 1, resetTimeout = 1000L)
    @Override
    public Foo process(@NotNull Foo foo) {
        final ResponseEntity<String> responseEntity = restTemplate.exchange(
                "http://enrich-service/enrich",
                HttpMethod.GET,
                null,
                String.class
        );

        foo.setMessage(responseEntity.getBody());
        return foo;
    }
}
