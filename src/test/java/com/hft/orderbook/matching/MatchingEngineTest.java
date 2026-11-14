package com.hft.orderbook.matching;

import com.hft.orderbook.book.OrderBook;
import com.hft.orderbook.model.Order;
import com.hft.orderbook.model.OrderSide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchingEngineTest {

    private OrderBook book;
    private MatchingEngine engine;

    @BeforeEach
    void setUp() {
        book = new OrderBook();
        engine = new MatchingEngine(book);
    }

    @Test
    void passiveBuyRestsOnBook() {
        MatchResult result = engine.submit(new Order(1, OrderSide.BUY, 100, 10));

        assertTrue(result.restingOnBook());
        assertEquals(0, result.filledQuantity());
        assertTrue(result.trades().isEmpty());
        assertEquals(100, book.getBestBid().getPrice());
    }

    @Test
    void fullMatchAtMakerPrice() {
        book.addOrder(new Order(1, OrderSide.SELL, 105, 10));

        MatchResult result = engine.submit(new Order(2, OrderSide.BUY, 110, 10));

        assertFalse(result.restingOnBook());
        assertEquals(10, result.filledQuantity());
        assertEquals(1, result.trades().size());
        Trade trade = result.trades().getFirst();
        assertEquals(105, trade.price());
        assertEquals(1, trade.makerOrderId());
        assertEquals(2, trade.takerOrderId());
        assertEquals(10, trade.quantity());
        assertNull(book.getBestAsk());
        assertFalse(book.isOpen(1));
        assertTrue(book.getOrderById(1).isFilled());
    }

    @Test
    void partialFillOnMakerRestsTakerRemainder() {
        book.addOrder(new Order(1, OrderSide.SELL, 100, 6));

        MatchResult result = engine.submit(new Order(2, OrderSide.BUY, 100, 10));

        assertTrue(result.restingOnBook());
        assertEquals(6, result.filledQuantity());
        assertEquals(4, result.remainingQuantity());
        assertNull(book.getBestAsk());
        assertEquals(4, book.getBestBid().totalRemainingQuantity());
    }

    @Test
    void fifoAtSamePriceLevel() {
        Order first = new Order(1, OrderSide.SELL, 100, 5);
        Order second = new Order(2, OrderSide.SELL, 100, 5);
        book.addOrder(first);
        book.addOrder(second);

        MatchResult result = engine.submit(new Order(3, OrderSide.BUY, 100, 7));

        assertEquals(2, result.trades().size());
        assertEquals(1, result.trades().get(0).makerOrderId());
        assertEquals(2, result.trades().get(1).makerOrderId());
        assertEquals(0, result.remainingQuantity());
        assertFalse(result.restingOnBook());
    }

    @Test
    void walksMultiplePriceLevels() {
        book.addOrder(new Order(1, OrderSide.SELL, 101, 10));
        book.addOrder(new Order(2, OrderSide.SELL, 100, 10));

        MatchResult result = engine.submit(new Order(3, OrderSide.BUY, 102, 15));

        assertEquals(2, result.trades().size());
        assertEquals(100, result.trades().get(0).price());
        assertEquals(101, result.trades().get(1).price());
        assertEquals(0, result.remainingQuantity());
        assertEquals(5, book.getBestAsk().totalRemainingQuantity());
    }

    @Test
    void sellTakerMatchesBids() {
        book.addOrder(new Order(1, OrderSide.BUY, 105, 20));

        MatchResult result = engine.submit(new Order(2, OrderSide.SELL, 100, 8));

        assertEquals(8, result.filledQuantity());
        assertEquals(105, result.trades().getFirst().price());
        assertEquals(12, book.getBestBid().totalRemainingQuantity());
    }

    @Test
    void doesNotCrossWhenPriceTooLow() {
        book.addOrder(new Order(1, OrderSide.SELL, 105, 10));

        MatchResult result = engine.submit(new Order(2, OrderSide.BUY, 104, 10));

        assertTrue(result.restingOnBook());
        assertEquals(0, result.filledQuantity());
        assertEquals(105, book.getBestAsk().getPrice());
        assertEquals(104, book.getBestBid().getPrice());
    }
}
