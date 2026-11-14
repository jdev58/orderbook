package com.hft.orderbook.book;

import com.hft.orderbook.model.Order;
import com.hft.orderbook.model.OrderSide;
import com.hft.orderbook.model.OrderStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderBookTest {

    @Test
    void shouldAddBuyOrdersAndMaintainPricePriority() {
        OrderBook book = new OrderBook();

        book.addOrder(new Order(1, OrderSide.BUY, 100, 10));
        book.addOrder(new Order(2, OrderSide.BUY, 105, 20));
        book.addOrder(new Order(3, OrderSide.BUY, 102, 15));

        assertEquals(105, book.getBestBid().getPrice());
    }

    @Test
    void shouldAddSellOrdersAndMaintainPricePriority() {
        OrderBook book = new OrderBook();

        book.addOrder(new Order(1, OrderSide.SELL, 110, 10));
        book.addOrder(new Order(2, OrderSide.SELL, 105, 20));
        book.addOrder(new Order(3, OrderSide.SELL, 108, 15));

        assertEquals(105, book.getBestAsk().getPrice());
    }

    @Test
    void shouldMaintainFifoWithinSamePriceLevel() {
        OrderBook book = new OrderBook();

        Order first = new Order(1, OrderSide.BUY, 100, 10);
        Order second = new Order(2, OrderSide.BUY, 100, 20);

        book.addOrder(first);
        book.addOrder(second);

        PriceLevel level = book.getBestBid();

        assertSame(first, level.getHead());
        assertSame(second, level.getTail());
        assertSame(second, first.getNext());
        assertSame(first, second.getPrev());
    }

    @Test
    void shouldReturnNullWhenBookSideIsEmpty() {
        OrderBook book = new OrderBook();

        assertNull(book.getBestBid());
        assertNull(book.getBestAsk());
    }

    @Test
void shouldCancelOrderFromMiddleOfPriceLevel() {
    OrderBook book = new OrderBook();

    Order first = new Order(1, OrderSide.BUY, 100, 10);
    Order middle = new Order(2, OrderSide.BUY, 100, 20);
    Order last = new Order(3, OrderSide.BUY, 100, 30);

    book.addOrder(first);
    book.addOrder(middle);
    book.addOrder(last);

    assertTrue(book.cancelOrder(2));

    assertSame(first.getNext(), last);
    assertSame(last.getPrev(), first);
    assertEquals(OrderStatus.CANCELLED, book.getOrderById(2).getStatus());
    assertNull(middle.getOwner());
}

@Test
void shouldCancelHeadOrder() {
    OrderBook book = new OrderBook();

    Order first = new Order(1, OrderSide.BUY, 100, 10);
    Order second = new Order(2, OrderSide.BUY, 100, 20);

    book.addOrder(first);
    book.addOrder(second);

    assertTrue(book.cancelOrder(1));

    assertSame(book.getBestBid().getHead(), second);
    assertNull(first.getOwner());
}

@Test
void shouldRemoveEmptyPriceLevelAfterCancellation() {
    OrderBook book = new OrderBook();

    Order order = new Order(1, OrderSide.BUY, 100, 10);

    book.addOrder(order);

    assertTrue(book.cancelOrder(1));

    assertNull(book.getBestBid());
    assertEquals(OrderStatus.CANCELLED, book.getOrderById(1).getStatus());
}

@Test
void shouldReturnFalseWhenOrderDoesNotExist() {
    OrderBook book = new OrderBook();

    assertFalse(book.cancelOrder(999));
}

    @Test
    void snapshotExposesDepthForVisualization() {
        OrderBook book = new OrderBook();
        book.addOrder(new Order(1, OrderSide.BUY, 100, 10));
        book.addOrder(new Order(2, OrderSide.BUY, 100, 20));
        book.addOrder(new Order(3, OrderSide.SELL, 105, 15));

        var snap = book.snapshot(5);

        assertEquals(100L, snap.bestBid());
        assertEquals(105L, snap.bestAsk());
        assertEquals(3, snap.openOrders());
        assertEquals(1, snap.bids().size());
        assertEquals(30L, snap.bids().getFirst().quantity());
        assertEquals(2, snap.bids().getFirst().orderCount());
    }
}
