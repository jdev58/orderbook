package com.hft.orderbook.model;

import com.hft.orderbook.book.PriceLevel;

public class Order {
     Order prev;
     Order next;
    private final long id;
    private final OrderSide side;
    private final long price;
    private final long quantity;

    private long remainingQuantity;

    private OrderStatus status = OrderStatus.NEW;

    private PriceLevel owner;

    public Order(long id, OrderSide side, long price, long quantity) {

        if (id <= 0) {
            throw new IllegalArgumentException("Order ID must be positive");
        }
    
        if (side == null) {
            throw new IllegalArgumentException("Order side cannot be null");
        }
    
        if (price <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
    
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    
        this.id = id;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.remainingQuantity = quantity;
    }

    public boolean isFilled() {
        return status == OrderStatus.FILLED;
    }

    public boolean isPartiallyFilled() {
        return status == OrderStatus.PARTIALLY_FILLED;
    }

    public boolean isCancelled() {
        return status == OrderStatus.CANCELLED;
    }

    public boolean isTerminal() {
        return status == OrderStatus.FILLED || status == OrderStatus.CANCELLED;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public boolean isOpen() {
        return owner != null && !isTerminal();
    }

    public long getId() {
        return id;
    }
    
    public OrderSide getSide() {
        return side;
    }
    
    public long getPrice() {
        return price;
    }
    
    public long getQuantity() {
        return quantity;
    }
    
    public long getRemainingQuantity() {
        return remainingQuantity;
    }

    public Order getPrev() {
        return prev;
    }

    public void setPrev(Order prev) {
        this.prev = prev;
    }

    public Order getNext() {
        return next;
    }

    public void setNext(Order next) {
        this.next = next;
    }

    public void fill(long executedQuantity) {

        if (executedQuantity <= 0) {
            throw new IllegalArgumentException(
                "Executed quantity must be positive"
            );
        }
    
        if (executedQuantity > remainingQuantity) {
            throw new IllegalArgumentException(
                "Executed quantity exceeds remaining quantity"
            );
        }
    
        remainingQuantity -= executedQuantity;
        refreshStatusAfterFill();
    }

    public void markCancelled() {
        if (status == OrderStatus.FILLED) {
            throw new IllegalStateException("Filled order cannot be cancelled");
        }
        if (status == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Order already cancelled");
        }
        status = OrderStatus.CANCELLED;
    }

    private void refreshStatusAfterFill() {
        if (remainingQuantity == 0) {
            status = OrderStatus.FILLED;
        } else if (remainingQuantity < quantity) {
            status = OrderStatus.PARTIALLY_FILLED;
        }
    }

    public PriceLevel getOwner() {
        return owner;
    }
    
    public void setOwner(PriceLevel owner) {
        this.owner = owner;
    }
}
