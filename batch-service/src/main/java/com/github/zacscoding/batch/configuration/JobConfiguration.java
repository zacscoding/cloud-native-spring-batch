package com.github.zacscoding.batch.configuration;

import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.MultiResourceItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.client.RestTemplate;

import com.github.zacscoding.batch.batch.EnrichmentProcessor;
import com.github.zacscoding.batch.batch.Foo;
import com.github.zacscoding.batch.batch.listener.LoggingChunkListener;
import com.github.zacscoding.batch.batch.listener.LoggingJobListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class JobConfiguration {

    private final StepBuilderFactory stepBuilderFactory;
    private final JobBuilderFactory jobBuilderFactory;

    @Bean
    @StepScope
    public MultiResourceItemReader<Foo> reader(@Value("#{jobExecutionContext['localFiles']}") String paths)
            throws Exception {

        logger.info("[ItemReader] >> paths = {}", paths);
        MultiResourceItemReader<Foo> reader = new MultiResourceItemReader<>();

        reader.setName("multiReader");
        reader.setDelegate(delegate());

        final String[] parsedPaths = paths.split(",");
        logger.info("[ItemReader] parsed paths: {}", parsedPaths.length);
        final List<Resource> resources = new ArrayList<>(parsedPaths.length);

        for (String parsedPath : parsedPaths) {
            final Resource resource = new FileSystemResource(parsedPath);
            logger.info("[ItemReader] >> resource = {}", resource.getURI());
            resources.add(resource);
        }
        reader.setResources(resources.toArray(new Resource[resources.size()]));

        return reader;
    }

    @Bean
    @StepScope
    FlatFileItemReader<Foo> delegate() throws Exception {
        return new FlatFileItemReaderBuilder<Foo>()
                .name("fooReader")
                .delimited()
                .names("first", "scond", "third")
                .targetType(Foo.class)
                .build();
    }

    @Bean
    @StepScope
    EnrichmentProcessor processor(RestTemplate restTemplate) {
        return new EnrichmentProcessor(restTemplate);
    }

    @Bean
    JdbcBatchItemWriter<Foo> writer(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<Foo>()
                .dataSource(dataSource)
                .beanMapped()
                .sql("INSERT INTO foo (first, second, third, message) VALUES (:first, :second, :third, :message)")
                .build();
    }

    @Bean
    Step load() throws Exception {
        return stepBuilderFactory.get("load")
                                 .<Foo, Foo>chunk(20)
                                 .reader(reader(null))
                                 .processor(processor(null))
                                 .writer(writer(null))
                                 .listener(new LoggingChunkListener())
                                 .build();
    }

    @Bean
    Job s3jdbcJob(JobExecutionListener jobExecutionListener) throws Exception {
        return jobBuilderFactory.get("s3jdbcJob")
                                //.incrementer(new RunIdIncrementer())
                                .listener(new LoggingJobListener())
                                .listener(jobExecutionListener)
                                .start(load())
                                .build();
    }
}
