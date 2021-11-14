package com.github.zacscoding.batch.batch;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
public class BatchController {

    private final JobLauncher jobLauncher;
    private final ApplicationContext context;
    private final RestTemplate restTemplate;

    @PostMapping("/batch/{jobName}")
    public ExitStatus startBatch(@PathVariable("jobName") String jobName) throws Exception {
        logger.info("Try to start batch: {}", jobName);

        final Job job = context.getBean(jobName, Job.class);
        final JobParameters jobParameters = new JobParametersBuilder()
                .addLong("startTimestamp", (long) LocalDateTime.now().getNano())
                .toJobParameters();

        return jobLauncher.run(job, jobParameters).getExitStatus();
    }

    @GetMapping("/call/enrich")
    public List<String> callEnrichApis() {
        return IntStream.range(0, 20).boxed().map(i -> {
            try {
                return restTemplate.exchange(
                        "http://localhost:8899/enrich",
                        HttpMethod.GET,
                        null,
                        String.class
                ).getBody();
            } catch (Exception e) {
                return e.getMessage();
            }
        }).collect(Collectors.toList());
    }
}
