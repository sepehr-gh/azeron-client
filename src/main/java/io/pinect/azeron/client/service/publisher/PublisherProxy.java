package io.pinect.azeron.client.service.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import nats.client.MessageHandler;
import org.springframework.cglib.proxy.InvocationHandler;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.Assert;

import java.lang.reflect.Method;

public class PublisherProxy implements InvocationHandler {
    private final EventMessagePublisher eventMessagePublisher;
    private final ObjectMapper objectMapper;
    private final TaskExecutor publisherThreadExecutor;


    public PublisherProxy(EventMessagePublisher eventMessagePublisher, ObjectMapper objectMapper, TaskExecutor publisherThreadExecutor) {
        this.eventMessagePublisher = eventMessagePublisher;
        this.objectMapper = objectMapper;
        this.publisherThreadExecutor = publisherThreadExecutor;
    }

    @Override
    public Object invoke(Object o, Method method, Object[] args) throws Throwable {
        Object message = (null != args && args.length > 0) ? args[0] : null;
        Object messageHandler = (null != args && args.length > 1) ? args[1] : null;

        Publisher publisher = method.getDeclaringClass().getAnnotation(Publisher.class);
        Async asyn = method.getAnnotation(Async.class);

        Assert.notNull(message, "Message can not be null");
        Assert.isTrue(messageHandler == null || messageHandler instanceof MessageHandler, "Second param must me message handler. Instance might be of different object");
        if(asyn == null){
            doPublish(message, (MessageHandler) messageHandler, publisher);
        }else {
            publisherThreadExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    doPublish(message, (MessageHandler) messageHandler, publisher);
                }
            });
        }
        return null;
    }

    @SneakyThrows
    private void doPublish(Object param, MessageHandler messageHandler, Publisher publisher){
        System.out.println(Thread.currentThread().getId());
        String messageBody = objectMapper.writeValueAsString(publisher.forClass().cast(param));
        eventMessagePublisher.sendMessage(publisher.eventName(), messageBody, publisher.publishStrategy(), messageHandler, publisher.raw());
    }
}
