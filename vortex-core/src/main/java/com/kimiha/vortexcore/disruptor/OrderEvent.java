package com.kimiha.vortexcore.disruptor;

import com.kimiha.vortexcore.model.OrderEntity;
import com.kimiha.vortexcore.model.CancellationEntity;
import lombok.Data;

@Data
public class OrderEvent {
    /** 下单请求：clOrderId、market、securityId、side、qty、price、shareholderId */
    private OrderEntity order;
    /** true 表示撤单请求，false 表示下单请求 */
    private boolean cancel;
    /** 撤单请求：clOrderId、origClOrderId、market、securityId、side、shareholderId */
    private CancellationEntity cancellation;
}