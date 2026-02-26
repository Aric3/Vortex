package com.kimiha.vortexcore.disruptor;

import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.SmartLifecycle;

@Configuration
public class DistruptorManager {

    private static final int ORDER_BUFFER_SIZE = 1024 * 16; // 16K
    private static final int PERSISTENCE_BUFFER_SIZE = 1024 * 8; // 8K

    @Bean
    public Disruptor<OrderEvent> orderDisruptor(OrderEventHandler handler) {
        Disruptor<OrderEvent> disruptor = new Disruptor<>(
                OrderEvent::new,
                ORDER_BUFFER_SIZE,
                DaemonThreadFactory.INSTANCE,
                ProducerType.MULTI,
                new YieldingWaitStrategy()
        );
        disruptor.handleEventsWith(handler);
        disruptor.start();
        return disruptor;
    }

    @Bean
    public Disruptor<PersistenceEvent> persistenceDisruptor(PersistenceEventHandler persistenceHandler) {
        Disruptor<PersistenceEvent> disruptor = new Disruptor<>(
                PersistenceEvent::new,
                PERSISTENCE_BUFFER_SIZE,
                DaemonThreadFactory.INSTANCE,
                ProducerType.MULTI,
                new YieldingWaitStrategy()
        );
        disruptor.handleEventsWith(persistenceHandler);
        disruptor.start();
        return disruptor;
    }

    /** 应用关闭时优雅停止 Order Disruptor */
    @Bean
    public SmartLifecycle disruptorLifecycle(Disruptor<OrderEvent> orderDisruptor) {
        return new SmartLifecycle() {
            private volatile boolean running = true;

            @Override
            public void start() {
                running = true;
            }

            @Override
            public void stop() {
                if (running) {
                    running = false;
                    orderDisruptor.shutdown();
                }
            }

            @Override
            public boolean isRunning() {
                return running;
            }

            @Override
            public int getPhase() {
                return Integer.MAX_VALUE - 100;
            }
        };
    }

    /** 应用关闭时优雅停止 Persistence Disruptor */
    @Bean
    public SmartLifecycle persistenceDisruptorLifecycle(Disruptor<PersistenceEvent> persistenceDisruptor) {
        return new SmartLifecycle() {
            private volatile boolean running = true;

            @Override
            public void start() {
                running = true;
            }

            @Override
            public void stop() {
                if (running) {
                    running = false;
                    persistenceDisruptor.shutdown();
                }
            }

            @Override
            public boolean isRunning() {
                return running;
            }

            @Override
            public int getPhase() {
                return Integer.MAX_VALUE - 99; // 在 order 之后停
            }
        };
    }
}