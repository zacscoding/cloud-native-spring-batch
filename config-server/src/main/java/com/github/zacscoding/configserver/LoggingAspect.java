package com.github.zacscoding.configserver;

import javax.annotation.PostConstruct;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.cloud.config.server.environment.EnvironmentController;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.config.server.environment.MultipleJGitEnvironmentProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Aspect
@RequiredArgsConstructor
public class LoggingAspect {

    private final MultipleJGitEnvironmentProperties properties;
    private final ObjectMapper objectMapper;

    @PostConstruct
    private void setUp() {
        logger.info("## Config server git uri: {}", properties.getUri());
    }

    /**
     * {@link EnvironmentRepository} 의 findOne() 메소드 호출 전 (파라미터, 구현체, Call Stack)을 출력
     */
    //@Before("execution(* org.springframework.cloud.config.server.environment.EnvironmentRepository.findOne(..))")
    public void environmentRepositoryOnBefore(JoinPoint joinPoint) {
        final StringBuilder stack = new StringBuilder();
        for (StackTraceElement elt : Thread.currentThread().getStackTrace()) {
            String callStack = elt.toString();
            if (StringUtils.startsWithIgnoreCase(callStack, "org.springframework.cloud.config")) {
                stack.append(callStack).append("\n");
            }
        }
        logger.info("// ====================================================================");
        logger.info("## EnvironmentRepository({})::findOne is called",
                    joinPoint.getTarget().getClass().getSimpleName());
        final Object[] args = joinPoint.getArgs();
        logger.info("application: {} / profile : {} / label : {}", args[0], args[1], args[2]);
        logger.info("==================================================================== //");
    }

    /* curl -XGET http://localhost:8888/demo/default 호출 시 출력 결과
    2020-07-28 21:26:10.142  INFO 9802 --- [nio-8888-exec-1] demo.server.EnvironmentRepositoryLogger  : ## EnvironmentRepository(MultipleJGitEnvironmentRepository)::findOne is called
    2020-07-28 21:26:10.143  INFO 9802 --- [nio-8888-exec-1] demo.server.EnvironmentRepositoryLogger  : application: demo / profile : dev / label : null
    org.springframework.cloud.config.server.environment.MultipleJGitEnvironmentRepository$$EnhancerBySpringCGLIB$$133b5593.findOne(<generated>)
    org.springframework.cloud.config.server.environment.CompositeEnvironmentRepository.findOne(CompositeEnvironmentRepository.java:58)
    org.springframework.cloud.config.server.environment.CompositeEnvironmentRepository$$FastClassBySpringCGLIB$$72bfc190.invoke(<generated>)
    org.springframework.cloud.config.server.environment.SearchPathCompositeEnvironmentRepository$$EnhancerBySpringCGLIB$$eaa1e8c9.findOne(<generated>)
    org.springframework.cloud.config.server.environment.EnvironmentEncryptorEnvironmentRepository.findOne(EnvironmentEncryptorEnvironmentRepository.java:61)
    org.springframework.cloud.config.server.environment.EnvironmentController.getEnvironment(EnvironmentController.java:136)
    org.springframework.cloud.config.server.environment.EnvironmentController.defaultLabel(EnvironmentController.java:108)
    org.springframework.cloud.config.server.environment.EnvironmentController$$EnhancerBySpringCGLIB$$9286781c.defaultLabel(<generated>)
     */

    /**
     * {@link EnvironmentController}의 Endpoint 호출 시 로깅
     */
    @Around("execution(* org.springframework.cloud.config.server.environment.EnvironmentController.default*(..))"
            + "|| execution(* org.springframework.cloud.config.server.environment.EnvironmentController.labelled*(..))")
    public Object environmentControllerOnBefore(ProceedingJoinPoint joinPoint) throws Throwable {
        final long start = System.currentTimeMillis();
        final String name = joinPoint.getArgs()[0].toString();
        final String profiles = joinPoint.getArgs()[1].toString();
        final String label = joinPoint.getArgs().length > 2 ? joinPoint.getArgs()[2].toString() : "null";
        Object result = null;

        try {
            result = joinPoint.proceed();
            return result;
        } finally {
            final long elapsed = System.currentTimeMillis() - start;
            logger.info("// ====================================================================");
            logger.info("## EnvironmentController called: {}", joinPoint.getSignature());
            logger.info(">> name: {} / profiles: {} / label: {}, elapsed: {}[ms]", name, profiles, label, elapsed);
            logger.info(">> result: {}", toString(result));
            logger.info("==================================================================== //");
        }
    }

    public String toString(Object object) {
        if (object == null) {
            return "NULL";
        }

        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            return object.toString();
        }
    }
}
