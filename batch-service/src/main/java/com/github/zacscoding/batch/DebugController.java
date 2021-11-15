package com.github.zacscoding.batch;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/debug")
@RequiredArgsConstructor
public class DebugController {

    private final RestTemplate restTemplate;
    private final DiscoveryClient discoveryClient;

    @GetMapping("/call")
    public String callEnrich() {
        try {
            final ResponseEntity<String> responseEntity = restTemplate.exchange(
                    "http://enrich-service/enrich",
                    HttpMethod.GET,
                    null,
                    String.class
            );
            return responseEntity.getBody();
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    @GetMapping("/discovery/services")
    public Map<String, List<ServiceInstance>> discoveryServices() {
        return discoveryClient.getServices()
                              .stream()
                              .collect(Collectors.toMap(s -> s, discoveryClient::getInstances));
    }
}
