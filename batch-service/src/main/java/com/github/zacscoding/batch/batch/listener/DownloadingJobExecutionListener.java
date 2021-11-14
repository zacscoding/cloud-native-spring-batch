package com.github.zacscoding.batch.batch.listener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListenerSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import com.amazonaws.services.s3.AmazonS3Client;

import io.awspring.cloud.core.io.s3.PathMatchingSimpleStorageResourcePatternResolver;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DownloadingJobExecutionListener extends JobExecutionListenerSupport {

    private final ResourcePatternResolver resourcePatternResolver;
    private final String path;

    @Autowired
    public DownloadingJobExecutionListener(ResourcePatternResolver resourcePatternResolver,
                                           AmazonS3Client amazonS3,
                                           @Value("${job.resource-path}") String path) {
        this.resourcePatternResolver = new PathMatchingSimpleStorageResourcePatternResolver(amazonS3,
                                                                                            resourcePatternResolver);
        this.path = path;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        try {
            final Resource[] resources = resourcePatternResolver.getResources(path);
            logger.info("found resources: {} in s3 path: {}", resources.length, path);

            final StringBuilder paths = new StringBuilder();
            for (Resource resource : resources) {
                File file = File.createTempFile("input", ".csv");

                StreamUtils.copy(resource.getInputStream(), new FileOutputStream(file));
                paths.append(file.getAbsolutePath()).append(",");

                logger.info("downloaded file: {}", file.getAbsolutePath());
            }

            final String localFiles = paths.substring(0, paths.length() - 1);
            logger.info("try to put localFiles {} to job execution context", localFiles);
            jobExecution.getExecutionContext().put("localFiles", localFiles);
        } catch (IOException e) {
            logger.error("Exception occur while executing beforeJob", e);
        }
    }
}
