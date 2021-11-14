package com.github.zacscoding.batch.batch.listener;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListenerSupport;

import com.github.zacscoding.batch.utils.ThreadUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LoggingJobListener extends JobExecutionListenerSupport {

    private final boolean printStackTrace = false;

    @Override
    public void beforeJob(JobExecution jobExecution) {
        logger.info("[JobListener] beforeJob");
        if (printStackTrace) {
            logger.info("\n{}", ThreadUtil.getStackTrace());
        }
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        logger.info("[JobListener] afterJob");
        if (printStackTrace) {
            logger.info("\n{}", ThreadUtil.getStackTrace());
        }
    }
}
