package com.hft.orderbook.model;

/**
 * Lifecycle of a limit order in the matching system.
 */
public enum OrderStatus {

    NEW,
    PARTIALLY_FILLED,
    FILLED,
    CANCELLED
}
