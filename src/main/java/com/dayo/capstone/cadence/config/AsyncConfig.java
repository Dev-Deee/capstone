package com.dayo.capstone.cadence.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Bounded executors for background work, so neither a burst of events nor a
 * slow mail provider can hold up a request a user is waiting on.
 * - eventExecutor: domain-event listeners (notifications).
 * - mailExecutor: outgoing email, including retries.
 * A full queue runs the task on the caller's thread rather than dropping it.
 */
@Slf4j
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Bean(name = "eventExecutor")
    public Executor eventExecutor() {
        return executor("event-", 2, 4);
    }

    @Bean(name = "mailExecutor")
    public Executor mailExecutor() {
        return executor("mail-", 1, 2);
    }

    private static ThreadPoolTaskExecutor executor(String prefix, int core, int max) {
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setThreadNamePrefix(prefix);
        e.setCorePoolSize(core);
        e.setMaxPoolSize(max);
        e.setQueueCapacity(500);
        e.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        e.setWaitForTasksToCompleteOnShutdown(true);
        e.setAwaitTerminationSeconds(10);
        e.initialize();
        return e;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> log.error("Async task failed: method={}", method.getName(), ex);
    }
}
