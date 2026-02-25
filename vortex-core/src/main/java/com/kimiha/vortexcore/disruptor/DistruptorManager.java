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

    @Bean
    public Disruptor<OrderEvent> orderDisruptor(OrderEventHandler handler) {
        int bufferSize = 1024 * 16; //16K个订单事件缓冲区

        Disruptor<OrderEvent> disruptor = new Disruptor<>(
                OrderEvent::new,
                bufferSize,
                DaemonThreadFactory.INSTANCE,
                ProducerType.MULTI, // 支持多个 Controller 线程同时写入
                new YieldingWaitStrategy() // 兼顾低延迟和 CPU 负载
        );

        disruptor.handleEventsWith(handler);
        disruptor.start();
        return disruptor;
    }

    /** 应用关闭时优雅停止 Disruptor：先排空再结束消费线程，避免 JVM 退出时线程被强杀、在途订单丢失 */
    @Bean
    public SmartLifecycle disruptorLifecycle(Disruptor<OrderEvent> orderDisruptor) {
        return new SmartLifecycle() {
            private volatile boolean running;

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
                return Integer.MAX_VALUE - 100; // 较晚关闭，让其它 Bean 先停
            }
        };
    }
}