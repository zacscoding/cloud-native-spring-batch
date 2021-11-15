package com.github.zacscoding.batch;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.retry.annotation.EnableRetry;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootApplication
@EnableRetry
@EnableBatchProcessing
@EnableDiscoveryClient(autoRegister = false)
public class BatchApplication {

    /*
    $ curl -XPOST http://localhost:8899/batch/s3jdbcJob | jq .
     */
    public static void main(String[] args) {
        SpringApplication.run(BatchApplication.class, args);
    }
}
