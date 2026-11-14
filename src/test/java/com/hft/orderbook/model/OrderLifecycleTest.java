package com.hft.orderbook.model;

import com.hft.orderbook.book.OrderBook;
import com.hft.orderbook.matching.MatchingEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderLifecycleTest {

    private OrderBook book;
    private MatchingEngine engine;

    @BeforeEach
    void setUp() {
        book = new OrderBook();
        engine = new MatchingEngine(book);
    }

    @Test
    void passiveOrderStaysNewUntilTrade() {
        Order order = new Order(1, OrderSide.BUY, 100, 10);
        book.addOrder(order);

        assertEquals(OrderStatus.NEW, order.getStatus());
        assertTrue(order.isOpen());
    }

    @Test
    void cancelSetsCancelledAndKeepsHistory() {
        Order order = new Order(1, OrderSide.BUY, 100, 10);
        book.addOrder(order);

        assertTrue(book.cancelOrder(1));

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertFalse(order.isOpen());
        assertEquals(order, book.getOrderById(1));
        assertNull(book.getBestBid());
    }

    @Test
    void filledMakerIsNotCancelled() {
        book.addOrder(new Order(1, OrderSide.SELL, 100, 10));

        engine.submit(new Order(2, OrderSide.BUY, 100, 10));

        Order maker = book.getOrderById(1);
        assertEquals(OrderStatus.FILLED, maker.getStatus());
        assertFalse(maker.isCancelled());
        assertFalse(book.isOpen(1));
    }

    @Test
    void partialFillThenCancel() {
        book.addOrder(new Order(1, OrderSide.SELL, 100, 10));

        engine.submit(new Order(2, OrderSide.BUY, 100, 4));
        assertEquals(OrderStatus.PARTIALLY_FILLED, book.getOrderById(1).getStatus());

        assertTrue(book.cancelOrder(1));
        assertEquals(OrderStatus.CANCELLED, book.getOrderById(1).getStatus());
        assertEquals(6, book.getOrderById(1).getRemainingQuantity());
    }

    @Test
    void replaceCancelsOldAndSubmitsNew() {
        book.addOrder(new Order(1, OrderSide.BUY, 100, 10));

        var result = engine.replace(1, 2, 101, 15);

        assertEquals(OrderStatus.CANCELLED, book.getOrderById(1).getStatus());
        assertEquals(OrderStatus.NEW, result.order().getStatus());
        assertEquals(101, book.getBestBid().getPrice());
        assertEquals(15, book.getBestBid().totalRemainingQuantity());
    }

    @Test
    void cannotCancelFilledOrder() {
        book.addOrder(new Order(1, OrderSide.SELL, 100, 5));
        engine.submit(new Order(2, OrderSide.BUY, 100, 5));

        assertFalse(book.cancelOrder(1));
        assertEquals(OrderStatus.FILLED, book.getOrderById(1).getStatus());
    }
}
