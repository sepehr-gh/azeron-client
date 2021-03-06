package io.pinect.azeron.client.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinect.azeron.client.config.properties.AzeronClientProperties;
import io.pinect.azeron.client.domain.repository.FallbackRepository;
import io.pinect.azeron.client.service.api.HostBasedAzeronInstancePinger;
import io.pinect.azeron.client.service.api.HostBasedNatsConfigProvider;
import io.pinect.azeron.client.service.api.NatsConfigProvider;
import io.pinect.azeron.client.service.api.Pinger;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.task.TaskExecutor;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.AlwaysRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.ErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAutoConfiguration
@ComponentScan("io.pinect.azeron.client")
@EnableConfigurationProperties({AzeronClientProperties.class})
@Log4j2
public class AzeronClientConfiguration {
    private final AzeronClientProperties azeronClientProperties;

    @Autowired
    public AzeronClientConfiguration(AzeronClientProperties azeronClientProperties) {
        this.azeronClientProperties = azeronClientProperties;
    }

    @Bean(name="publisherThreadExecutor")
    public TaskExecutor publisherThreadExecutor(){
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(azeronClientProperties.getAsyncThreadPoolCorePoolSize());
        exec.setMaxPoolSize(azeronClientProperties.getAsyncThreadPoolMaxPoolSize());
        exec.setQueueCapacity(azeronClientProperties.getAsyncThreadPoolQueueCapacity());
        exec.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        exec.setThreadPriority(Thread.MAX_PRIORITY);
        exec.setThreadNamePrefix("publisher-thread");
        exec.initialize();
        return exec;
    }

    @Bean("objectMapper")
    @ConditionalOnMissingBean(value = ObjectMapper.class)
    public ObjectMapper objectMapper(){
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return objectMapper;
    }

    @Bean("seenExecutor")
    public Executor seenExecutor(){
        ThreadPoolTaskExecutor threadPoolTaskExecutor = new ThreadPoolTaskExecutor();
        threadPoolTaskExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        threadPoolTaskExecutor.setWaitForTasksToCompleteOnShutdown(true);
        threadPoolTaskExecutor.setQueueCapacity(100);
        threadPoolTaskExecutor.setMaxPoolSize(100);
        threadPoolTaskExecutor.setCorePoolSize(20);
        threadPoolTaskExecutor.setDaemon(true);
        threadPoolTaskExecutor.setThreadNamePrefix("seen_executor_");
        threadPoolTaskExecutor.setBeanName("seenExecutor");
        threadPoolTaskExecutor.setAwaitTerminationSeconds(10);
        return threadPoolTaskExecutor;
    }

    @Bean("eventPublishRetryTemplate")
    @Scope("singleton")
    public RetryTemplate eventPublishRetryTemplate(){
        FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
        backOffPolicy.setBackOffPeriod(5000); // 5 seconds

        RetryTemplate template = new RetryTemplate();
        template.setRetryPolicy(new AlwaysRetryPolicy());
        template.setBackOffPolicy(backOffPolicy);

        return template;
    }

    @Bean
    @ConditionalOnMissingBean(NatsConfigProvider.class)
    public NatsConfigProvider natsConfigProvider(){
        return new HostBasedNatsConfigProvider(new RestTemplate(), azeronClientProperties);
    }

    @Bean
    @ConditionalOnMissingBean(Pinger.class)
    public Pinger pinger(){
        return new HostBasedAzeronInstancePinger(azeronClientProperties);
    }

    @Bean
    @ConditionalOnMissingBean(FallbackRepository.class)
    public FallbackRepository fallbackRepository(){
        return new FallbackRepository.VoidFallbackRepository();
    }

    @Bean("azeronTaskScheduler")
    public TaskScheduler azeronTaskScheduler() {
        ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
        threadPoolTaskScheduler.setPoolSize(20);
        threadPoolTaskScheduler.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        threadPoolTaskScheduler.setRemoveOnCancelPolicy(false);
        threadPoolTaskScheduler.setErrorHandler(new ErrorHandler() {
            @Override
            public void handleError(Throwable throwable) {
                log.error("Error in azeronTaskScheduler", throwable);
            }
        });
        threadPoolTaskScheduler.setThreadGroupName("azeron_server");
        threadPoolTaskScheduler.setThreadPriority(Thread.MAX_PRIORITY);
        threadPoolTaskScheduler.setBeanName("azeronTaskScheduler");
        threadPoolTaskScheduler.initialize();
        return threadPoolTaskScheduler;
    }

}
