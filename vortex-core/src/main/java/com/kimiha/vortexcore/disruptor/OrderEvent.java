package com.kimiha.vortexcore.disruptor;

import com.kimiha.vortexcore.model.OrderEntity;
import lombok.Data;

@Data
public class OrderEvent {
    private OrderEntity order;
}