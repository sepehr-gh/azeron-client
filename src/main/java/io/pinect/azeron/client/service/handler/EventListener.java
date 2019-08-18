package io.pinect.azeron.client.service.handler;

import io.pinect.azeron.client.domain.HandlerPolicy;
import io.pinect.azeron.client.domain.ProcessErrorStrategy;
import io.pinect.azeron.client.domain.dto.out.MessageDto;
import io.pinect.azeron.client.domain.model.ClientConfig;
import nats.client.MessageHandler;
import org.springframework.lang.Nullable;

public interface EventListener<E> extends MessageHandler {
    HandlerPolicy policy();
    Class<E> eClass();
    AzeronMessageProcessor<E> azeronMessageProcessor();
    AzeronErrorHandler azeronErrorHandler();
    ProcessErrorStrategy processErrorStrategy();
    String eventName();
    ClientConfig clientConfig();
    default boolean useAzeron(){
        return policy().equals(HandlerPolicy.ASYNC_SEEN) || policy().equals(HandlerPolicy.FULL);
    }
    void handle(String messageBody);

    interface AzeronErrorHandler {
        void onError(Exception e, @Nullable MessageDto messageDto);
    }

    interface AzeronMessageProcessor<E> {
        void process(E e);
    }
}