package com.github.zacscoding.batch.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class S3Configuration {

    private final AWSCredentialsProvider credentialsProvider;
    private final String endpoint;
    private final String region;

    public S3Configuration(AWSCredentialsProvider credentialsProvider,
                           @Value("${cloud.aws.s3.endpoint}") String endpoint,
                           @Value("${cloud.aws.region.static}") String region) {
        this.credentialsProvider = credentialsProvider;
        this.endpoint = endpoint;
        this.region = region;
    }

    /**
     * spring-cloud-starter-aws에서 제공하는 AmazonS3Client를 사용하는 경우
     * -> Header에 "Host: {bucket}.localhost" 로 요청하게되어 404 나옴.
     *   - https://docs.aws.amazon.com/AmazonS3/latest/API/API_CreateBucket.html
     * -> /etc/hosts에 127.0.0.1   {bucket}.localhost 추가하면 LocalStack에서 Internal Error 발생
     * -> 직접 withPathStyleAccessEnabled(true)를 설정하여 {@link AmazonS3Client}를 생성한다.
     *   - https://github.com/localstack/localstack/issues/43
     * -> 또한 LocalStack latest에서는 동작하지 않음
     */
    @Primary
    @Bean
    AmazonS3Client amazonS3Client() {
        return (AmazonS3Client) AmazonS3ClientBuilder.standard()
                                                     .withCredentials(credentialsProvider)
                                                     .withEndpointConfiguration(
                                                             new EndpointConfiguration(endpoint, region))
                                                     .withPathStyleAccessEnabled(true)
                                                     .build();
    }
}
