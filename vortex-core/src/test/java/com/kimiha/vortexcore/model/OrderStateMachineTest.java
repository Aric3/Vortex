package com.kimiha.vortexcore.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OrderStateMachineTest {

    @Test
    void canTransition_newToPartiallyFilledOrFilledOrCanceled() {
        assertTrue(OrderStateMachine.canTransition(OrderStatus.New, OrderStatus.PartiallyFilled));
        assertTrue(OrderStateMachine.canTransition(OrderStatus.New, OrderStatus.Filled));
        assertTrue(OrderStateMachine.canTransition(OrderStatus.New, OrderStatus.Canceled));
        assertFalse(OrderStateMachine.canTransition(OrderStatus.New, OrderStatus.Rejected));
    }

    @Test
    void canTransition_partiallyFilledToFilledOrCanceled() {
        assertTrue(OrderStateMachine.canTransition(OrderStatus.PartiallyFilled, OrderStatus.Filled));
        assertTrue(OrderStateMachine.canTransition(OrderStatus.PartiallyFilled, OrderStatus.Canceled));
        assertFalse(OrderStateMachine.canTransition(OrderStatus.PartiallyFilled, OrderStatus.New));
    }

    @Test
    void canTransition_terminalStatesNoTransition() {
        assertFalse(OrderStateMachine.canTransition(OrderStatus.Filled, OrderStatus.Canceled));
        assertFalse(OrderStateMachine.canTransition(OrderStatus.Canceled, OrderStatus.New));
        assertFalse(OrderStateMachine.canTransition(OrderStatus.Rejected, OrderStatus.New));
    }

    @Test
    void canCancel_onlyNewOrPartiallyFilled() {
        assertTrue(OrderStateMachine.canCancel(OrderStatus.New));
        assertTrue(OrderStateMachine.canCancel(OrderStatus.PartiallyFilled));
        assertFalse(OrderStateMachine.canCancel(OrderStatus.Filled));
        assertFalse(OrderStateMachine.canCancel(OrderStatus.Canceled));
        assertFalse(OrderStateMachine.canCancel(OrderStatus.Rejected));
    }
}
