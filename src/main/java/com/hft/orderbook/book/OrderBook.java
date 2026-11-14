package com.hft.orderbook.book;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

import org.agrona.collections.Long2ObjectHashMap;

import com.hft.orderbook.model.Order;
import com.hft.orderbook.model.OrderSide;

public class OrderBook {

    /** Resting orders on the book (price–time queues). */
    private final Long2ObjectHashMap<Order> openOrdersById =
            new Long2ObjectHashMap<>();

    /** All orders seen by the system (open, filled, cancelled). */
    private final Long2ObjectHashMap<Order> allOrdersById =
            new Long2ObjectHashMap<>();
    private final NavigableMap<Long, PriceLevel> bids
            = new TreeMap<>(Comparator.reverseOrder());

    private final NavigableMap<Long, PriceLevel> asks
            = new TreeMap<>();

    private PriceLevel getOrCreatePriceLevel(
            NavigableMap<Long, PriceLevel> levels,
            long price
    ) {
        PriceLevel level = levels.get(price);

        if (level == null) {
            level = new PriceLevel(price);
            levels.put(price, level);
        }

        return level;
    }

    public void register(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }
        if (allOrdersById.get(order.getId()) != null) {
            throw new IllegalStateException(
                    "Order ID already exists: " + order.getId()
            );
        }
        allOrdersById.put(order.getId(), order);
    }

    public void addOrder(Order order) {

        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }

        if (allOrdersById.get(order.getId()) == null) {
            register(order);
        }

        if (order.isTerminal()) {
            throw new IllegalStateException(
                    "Cannot add terminal order to book: " + order.getId()
            );
        }

        if (openOrdersById.get(order.getId()) != null) {
            throw new IllegalStateException(
                    "Order already open on book: " + order.getId()
            );
        }

        NavigableMap<Long, PriceLevel> levels =
                order.getSide() == OrderSide.BUY ? bids : asks;

        PriceLevel level =
                getOrCreatePriceLevel(levels, order.getPrice());

        level.addLast(order);

        openOrdersById.put(order.getId(), order);
    }

    public PriceLevel getBestBid() {
        return bids.isEmpty() ? null : bids.firstEntry().getValue();
    }

    public PriceLevel getBestAsk() {
        return asks.isEmpty() ? null : asks.firstEntry().getValue();
    }

    public Order getOrderById(long orderId) {
        return allOrdersById.get(orderId);
    }

    public boolean isOpen(long orderId) {
        return openOrdersById.containsKey(orderId);
    }

  /** Removes a fully filled resting order from the book (status stays {@link com.hft.orderbook.model.OrderStatus#FILLED}). */
    public boolean removeFilledOrder(long orderId) {
        Order order = openOrdersById.get(orderId);
        if (order == null) {
            return false;
        }
        if (!order.isFilled()) {
            throw new IllegalStateException(
                    "Order is not fully filled: " + orderId
            );
        }
        detachFromBook(order);
        openOrdersById.remove(orderId);
        return true;
    }

    public boolean cancelOrder(long orderId) {

        Order order = openOrdersById.get(orderId);

        if (order == null) {
            return false;
        }

        order.markCancelled();
        detachFromBook(order);
        openOrdersById.remove(orderId);

        return true;
    }

    private void detachFromBook(Order order) {
        PriceLevel level = order.getOwner();
        level.remove(order);

        if (level.isEmpty()) {
            NavigableMap<Long, PriceLevel> levels =
                    order.getSide() == OrderSide.BUY ? bids : asks;

            levels.remove(order.getPrice());
        }
    }

    public int openOrderCount() {
        return openOrdersById.size();
    }

    public BookSnapshot snapshot(int maxLevels) {
        if (maxLevels <= 0) {
            throw new IllegalArgumentException("maxLevels must be positive");
        }

        List<BookLevelView> bidViews = levelViews(bids, maxLevels);
        List<BookLevelView> askViews = levelViews(asks, maxLevels);

        Long bestBid = bids.isEmpty() ? null : bids.firstKey();
        Long bestAsk = asks.isEmpty() ? null : asks.firstKey();

        Long spread = null;
        Double mid = null;
        if (bestBid != null && bestAsk != null) {
            spread = bestAsk - bestBid;
            mid = (bestBid + bestAsk) / 2.0;
        }

        return new BookSnapshot(
                bestBid,
                bestAsk,
                spread,
                mid,
                openOrderCount(),
                bidViews,
                askViews
        );
    }

    private static List<BookLevelView> levelViews(
            NavigableMap<Long, PriceLevel> levels,
            int maxLevels
    ) {
        List<BookLevelView> views = new ArrayList<>();
        int count = 0;
        for (PriceLevel level : levels.values()) {
            if (count++ >= maxLevels) {
                break;
            }
            views.add(new BookLevelView(
                    level.getPrice(),
                    level.totalRemainingQuantity(),
                    level.orderCount()
            ));
        }
        return views;
    }
}
