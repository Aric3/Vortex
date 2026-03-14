package com.kimiha.vortexcore.disruptor;

import com.kimiha.vortexcore.disruptor.order.OrderEvent;
import com.kimiha.vortexcore.disruptor.order.OrderEventHandler;
import com.kimiha.vortexcore.disruptor.order.ShardDisruptorHolder;
import com.kimiha.vortexcore.disruptor.persistance.PersistenceEvent;
import com.kimiha.vortexcore.disruptor.persistance.PersistenceEventHandler;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.SmartLifecycle;

@Configuration
public class DistruptorManager {

    private static final int PERSISTENCE_BUFFER_SIZE = 1024 * 16; // 16K，降低持久化背压到撮合线程的风险

    /** 分片 Disruptor 持有者；OrderService 按 securityId 直投对应分片 RingBuffer，保证一支股票只有一个消费者。 */
    @Bean
    public ShardDisruptorHolder shardDisruptorHolder(
            OrderEventHandler orderEventHandler,
            @Value("${vortex.matching.shards:1}") int shardCount) {
        return new ShardDisruptorHolder(shardCount, orderEventHandler);
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

    /** 应用关闭时停止分片 Disruptor */
    @Bean
    public SmartLifecycle shardDisruptorLifecycle(ShardDisruptorHolder shardDisruptorHolder) {
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
                    for (Disruptor<OrderEvent> d : shardDisruptorHolder.getShardDisruptors()) {
                        d.shutdown();
                    }
                }
            }

            @Override
            public boolean isRunning() {
                return running;
            }

            @Override
            public int getPhase() {
                return Integer.MAX_VALUE - 101;
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