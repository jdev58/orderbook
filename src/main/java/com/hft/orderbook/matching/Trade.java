package com.hft.orderbook.matching;

public record Trade(
        long tradeId,
        long makerOrderId,
        long takerOrderId,
        long price,
        long quantity
) {
}
