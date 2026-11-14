package com.hft.orderbook.book;

import com.hft.orderbook.model.Order;
import com.hft.orderbook.model.OrderSide;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PriceLevelTest {

    @Test
    void shouldRemoveOnlyOrder() {
        PriceLevel level = new PriceLevel(1000);

        Order order = new Order(1, OrderSide.BUY, 1000, 10);

        level.addLast(order);
        level.remove(order);

        assertNull(order.getOwner());
        assertNull(order.getPrev());
        assertNull(order.getNext());
    }

    @Test
    void shouldRemoveHead() {
        PriceLevel level = new PriceLevel(1000);

        Order first = new Order(1, OrderSide.BUY, 1000, 10);
        Order second = new Order(2, OrderSide.BUY, 1000, 20);

        level.addLast(first);
        level.addLast(second);

        level.remove(first);

        assertNull(first.getOwner());
        assertEquals(second, level.getHead());
    }

    @Test
    void shouldRemoveTail() {
        PriceLevel level = new PriceLevel(1000);

        Order first = new Order(1, OrderSide.BUY, 1000, 10);
        Order second = new Order(2, OrderSide.BUY, 1000, 20);

        level.addLast(first);
        level.addLast(second);

        level.remove(second);

        assertNull(second.getOwner());
        assertEquals(first, level.getTail());
    }

    @Test
    void shouldRemoveMiddle() {
        PriceLevel level = new PriceLevel(1000);

        Order first = new Order(1, OrderSide.BUY, 1000, 10);
        Order middle = new Order(2, OrderSide.BUY, 1000, 20);
        Order last = new Order(3, OrderSide.BUY, 1000, 30);

        level.addLast(first);
        level.addLast(middle);
        level.addLast(last);

        level.remove(middle);

        assertEquals(last, first.getNext());
        assertEquals(first, last.getPrev());
        assertNull(middle.getOwner());
    }
}
