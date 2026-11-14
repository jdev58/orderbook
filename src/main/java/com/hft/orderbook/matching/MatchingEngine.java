package com.hft.orderbook.matching;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.hft.orderbook.book.OrderBook;
import com.hft.orderbook.book.PriceLevel;
import com.hft.orderbook.model.Order;
import com.hft.orderbook.model.OrderSide;

/**
 * Price–time matching: best contra price first, FIFO at each {@link PriceLevel}.
 * Trade price is always the resting (maker) order price.
 */
public class MatchingEngine {

    private final OrderBook book;
    private final AtomicLong nextTradeId = new AtomicLong(1);

    public MatchingEngine(OrderBook book) {
        if (book == null) {
            throw new IllegalArgumentException("Order book cannot be null");
        }
        this.book = book;
    }

    public OrderBook book() {
        return book;
    }

    public MatchResult submit(Order taker) {
        if (taker == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }
        if (taker.getOwner() != null) {
            throw new IllegalStateException("Order is already on the book");
        }

        book.register(taker);

        long quantityBefore = taker.getRemainingQuantity();
        List<Trade> trades = new ArrayList<>();

        while (taker.getRemainingQuantity() > 0 && isCrossing(taker)) {
            PriceLevel level = contraBestLevel(taker.getSide());
            Order maker = level.getHead();

            long tradeQty = Math.min(
                    taker.getRemainingQuantity(),
                    maker.getRemainingQuantity()
            );
            long tradePrice = maker.getPrice();
            long tradeId = nextTradeId.getAndIncrement();

            maker.fill(tradeQty);
            taker.fill(tradeQty);

            trades.add(new Trade(
                    tradeId,
                    maker.getId(),
                    taker.getId(),
                    tradePrice,
                    tradeQty
            ));

            if (maker.isFilled()) {
                book.removeFilledOrder(maker.getId());
            }
        }

        boolean resting = taker.getRemainingQuantity() > 0;
        if (resting) {
            book.addOrder(taker);
        }

        long filled = quantityBefore - taker.getRemainingQuantity();
        return new MatchResult(taker, filled, taker.getRemainingQuantity(), trades, resting);
    }

    /**
     * Cancels an open order and submits a new one (new order id).
     */
    public MatchResult replace(long openOrderId, long newOrderId, long newPrice, long newQuantity) {
        Order existing = book.getOrderById(openOrderId);
        if (existing == null || !book.isOpen(openOrderId)) {
            throw new IllegalArgumentException("No open order with id: " + openOrderId);
        }
        OrderSide side = existing.getSide();
        if (!book.cancelOrder(openOrderId)) {
            throw new IllegalStateException("Failed to cancel order: " + openOrderId);
        }
        return submit(new Order(newOrderId, side, newPrice, newQuantity));
    }

    private boolean isCrossing(Order taker) {
        if (taker.getSide() == OrderSide.BUY) {
            PriceLevel bestAsk = book.getBestAsk();
            return bestAsk != null && bestAsk.getPrice() <= taker.getPrice();
        }
        PriceLevel bestBid = book.getBestBid();
        return bestBid != null && bestBid.getPrice() >= taker.getPrice();
    }

    private PriceLevel contraBestLevel(OrderSide takerSide) {
        return takerSide == OrderSide.BUY ? book.getBestAsk() : book.getBestBid();
    }
}
