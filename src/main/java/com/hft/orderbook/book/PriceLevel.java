package com.hft.orderbook.book;

import com.hft.orderbook.model.Order;

public class PriceLevel {

    private final long price;

    private Order head;
    private Order tail;

    public PriceLevel(long price) {
        this.price = price;
    }

    public long getPrice() {
        return price;
    }

    public boolean isEmpty() {
        return head == null;
    }

    public Order getHead() {
        return head;
    }

    public Order getTail() {
        return tail;
    }

    /**
     * Inserts {@code order} at the tail (FIFO / time priority at this price).
     */
    public void addLast(Order order) {

        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }
    
        if (order.getPrice() != price) {
            throw new IllegalArgumentException(
                    "Order price does not match PriceLevel price"
            );
        }
    
        if (order.getOwner() != null) {
            throw new IllegalStateException(
                    "Order already belongs to a PriceLevel"
            );
        }
    
        if (order.getPrev() != null || order.getNext() != null) {
            throw new IllegalStateException(
                    "Order is already linked"
            );
        }
    
        if (tail == null) {
            // First order in this PriceLevel
            head = order;
            tail = order;
        } else {
            // Append order to the end of FIFO list
            tail.setNext(order);
            order.setPrev(tail);
            tail = order;
        }
    
        order.setOwner(this);
    }

    public void remove(Order order) {

            if (order == null) {
                throw new IllegalArgumentException("Order cannot be null");
            }
        
            if (order.getOwner() != this) {
                throw new IllegalStateException(
                        "Order does not belong to this PriceLevel"
                );
            }
        
            Order prev = order.getPrev();
            Order next = order.getNext();
        
            // Update previous order or head
            if (prev == null) {
                head = next;
            } else {
                prev.setNext(next);
            }
        
            // Update next order or tail
            if (next == null) {
                tail = prev;
            } else {
                next.setPrev(prev);
            }
        
            // Detach the removed order
            order.setPrev(null);
            order.setNext(null);
            order.setOwner(null);
        }
    

    public int orderCount() {
        int count = 0;
        for (Order current = head; current != null; current = current.getNext()) {
            count++;
        }
        return count;
    }

    public long totalRemainingQuantity() {
        long total = 0;
        for (Order current = head; current != null; current = current.getNext()) {
            total += current.getRemainingQuantity();
        }
        return total;
    }

   
}
