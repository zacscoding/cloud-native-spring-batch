package com.github.zacscoding.batch.batch.listener;

import org.springframework.batch.core.ChunkListener;
import org.springframework.batch.core.scope.context.ChunkContext;

import com.github.zacscoding.batch.utils.ThreadUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LoggingChunkListener implements ChunkListener {

    private final boolean printStackTrace = false;

    @Override
    public void beforeChunk(ChunkContext context) {
        logger.info("[ChunkListener] beforeChunk");
        if (printStackTrace) {
            logger.info("\n{}", ThreadUtil.getStackTrace());
        }
    }

    @Override
    public void afterChunk(ChunkContext context) {
        logger.info("[ChunkListener] afterChunk");
        if (printStackTrace) {
            logger.info("\n{}", ThreadUtil.getStackTrace());
        }
    }

    @Override
    public void afterChunkError(ChunkContext context) {}
}