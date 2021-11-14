package com.github.zacscoding.configserver;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.cloud.config.server.environment.EnvironmentController;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Aspect
public class LoggingAspect {

    /**
     * {@link EnvironmentRepository} 의 findOne() 메소드 호출 전 (파라미터, 구현체, Call Stack)을 출력
     */
    @Before("execution(* org.springframework.cloud.config.server.environment.EnvironmentRepository.findOne(..))")
    public void environmentRepositoryOnBefore(JoinPoint joinPoint) {
        logger.debug("## EnvironmentRepository({})::findOne is called",
                     joinPoint.getTarget().getClass().getSimpleName());

        StringBuilder stack = new StringBuilder();
        for (StackTraceElement elt : Thread.currentThread().getStackTrace()) {
            String callStack = elt.toString();
            if (StringUtils.startsWithIgnoreCase(callStack, "org.springframework.cloud.config")) {
                stack.append(callStack).append("\n");
            }
        }

        Object[] args = joinPoint.getArgs();
        logger.debug("application: {} / profile : {} / label : {}\n{}", args[0], args[1], args[2], stack.toString());
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
    @Before("execution(* org.springframework.cloud.config.server.environment.EnvironmentController.default*(..))"
            + "|| execution(* org.springframework.cloud.config.server.environment.EnvironmentController.labelled*(..))")
    public void environmentControllerOnBefore(JoinPoint joinPoint) {
        logger.debug("## EnvironmentController called: {}", joinPoint.getSignature());

        String name = joinPoint.getArgs()[0].toString();
        String profiles = joinPoint.getArgs()[1].toString();
        String label = joinPoint.getArgs().length > 2 ? joinPoint.getArgs()[2].toString() : "null";

        logger.debug("name : {} / profiles : {} / label : {}", name, profiles, label);
    }
}
