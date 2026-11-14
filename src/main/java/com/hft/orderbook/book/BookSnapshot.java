package com.hft.orderbook.book;

import java.util.List;

public record BookSnapshot(
        Long bestBid,
        Long bestAsk,
        Long spread,
        Double mid,
        int openOrders,
        List<BookLevelView> bids,
        List<BookLevelView> asks
) {
}
