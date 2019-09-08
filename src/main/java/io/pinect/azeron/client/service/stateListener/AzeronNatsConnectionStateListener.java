package io.pinect.azeron.client.service.stateListener;

import io.pinect.azeron.client.AtomicNatsHolder;
import io.pinect.azeron.client.service.ApplicationInitializer;
import io.pinect.azeron.client.service.EventListenerRegistry;
import io.pinect.azeron.client.service.NatsConnectionUpdater;
import io.pinect.azeron.client.service.api.NatsConfigProvider;
import lombok.extern.log4j.Log4j2;
import nats.client.ConnectionStateListener;
import nats.client.Nats;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

@Service("natsConnectionStateListener")
@Log4j2
public class AzeronNatsConnectionStateListener implements NatsConnectionStateListener{
    private ConnectionStateListener.State state;
    private final NatsConnectionUpdater natsConnectionUpdater;
    private final EventListenerRegistry eventListenerRegistry;
    private final ApplicationInitializer applicationInitializer;

    @Autowired
    public AzeronNatsConnectionStateListener(NatsConfigProvider natsConfigProvider, AtomicNatsHolder atomicNatsHolder, ApplicationContext applicationContext, EventListenerRegistry eventListenerRegistry, ApplicationInitializer applicationInitializer) {
        this.eventListenerRegistry = eventListenerRegistry;
        this.applicationInitializer = applicationInitializer;
        this.natsConnectionUpdater = new NatsConnectionUpdater(natsConfigProvider, atomicNatsHolder, applicationContext);
    }

    @Override
    public ConnectionStateListener.State getCurrentState() {
        return this.state;
    }

    @Override
    public void onConnectionStateChange(Nats nats, ConnectionStateListener.State state) {
        log.info("Nats state changed from "+ this.state + " to "+ state);
        boolean hasChanged = !state.equals(this.state);
        switch (state){
            case CONNECTED:
                if(hasChanged){
                    eventListenerRegistry.reRegisterAll();
                    applicationInitializer.initialize();
                }
                break;
            case DISCONNECTED:
                natsConnectionUpdater.update(this);
                applicationInitializer.destroy();
                break;
        }

        this.state = state;
    }
}
