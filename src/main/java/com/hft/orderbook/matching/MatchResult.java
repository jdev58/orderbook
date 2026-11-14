package com.hft.orderbook.matching;

import java.util.List;

import com.hft.orderbook.model.Order;
import com.hft.orderbook.model.OrderStatus;

public record MatchResult(
        Order order,
        long filledQuantity,
        long remainingQuantity,
        List<Trade> trades,
        boolean restingOnBook,
        OrderStatus status
) {
    public MatchResult(
            Order order,
            long filledQuantity,
            long remainingQuantity,
            List<Trade> trades,
            boolean restingOnBook
    ) {
        this(order, filledQuantity, remainingQuantity, trades, restingOnBook, order.getStatus());
    }
}
