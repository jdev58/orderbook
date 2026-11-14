package com.hft.orderbook.bench;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import com.hft.orderbook.book.OrderBook;
import com.hft.orderbook.matching.MatchResult;
import com.hft.orderbook.matching.MatchingEngine;
import com.hft.orderbook.model.Order;
import com.hft.orderbook.model.OrderSide;

/**
 * Baseline throughput for the current {@link TreeMap} + FIFO implementation.
 * Run all (Windows): {@code benchmarks.bat}
 * Run all: {@code mvn -Pjmh package -DskipTests && java -jar target/benchmarks.jar}
 * One benchmark: {@code java -jar target/benchmarks.jar -wi 1 -i 3 .*restingSubmit}
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
public class OrderBookBenchmarks {

    @State(Scope.Benchmark)
    public static class RestingSubmitState {
        OrderBook book;
        MatchingEngine engine;
        AtomicLong nextId = new AtomicLong(1);

        @Setup(Level.Trial)
        public void init() {
            book = new OrderBook();
            engine = new MatchingEngine(book);
        }
    }

    @Benchmark
    public MatchResult restingSubmit(RestingSubmitState s) {
        long id = s.nextId.getAndIncrement();
        long price = 50 + (id % 40);
        return s.engine.submit(new Order(id, OrderSide.BUY, price, 10));
    }

    @State(Scope.Benchmark)
    public static class SingleLevelMatchState {
        OrderBook book;
        MatchingEngine engine;
        AtomicLong nextId = new AtomicLong(2);

        @Setup(Level.Iteration)
        public void resetBook() {
            book = new OrderBook();
            engine = new MatchingEngine(book);
            book.addOrder(new Order(1, OrderSide.SELL, 100, 5_000_000));
            nextId.set(2);
        }
    }

    @Benchmark
    public MatchResult singleLevelMatch(SingleLevelMatchState s, Blackhole blackhole) {
        MatchResult result = s.engine.submit(
                new Order(s.nextId.getAndIncrement(), OrderSide.BUY, 100, 1)
        );
        blackhole.consume(result.trades());
        return result;
    }

    @State(Scope.Benchmark)
    public static class MultiLevelMatchState {
        OrderBook book;
        MatchingEngine engine;
        AtomicLong nextId = new AtomicLong(100);

        @Setup(Level.Iteration)
        public void resetBook() {
            book = new OrderBook();
            engine = new MatchingEngine(book);
            long makerId = 1;
            for (long price = 100; price < 110; price++) {
                book.addOrder(new Order(makerId++, OrderSide.SELL, price, 500_000));
            }
            nextId.set(1_000);
        }
    }

    @Benchmark
    public MatchResult multiLevelMatch(MultiLevelMatchState s, Blackhole blackhole) {
        MatchResult result = s.engine.submit(
                new Order(s.nextId.getAndIncrement(), OrderSide.BUY, 200, 3)
        );
        blackhole.consume(result.trades());
        return result;
    }

    @State(Scope.Benchmark)
    public static class CancelState {
        OrderBook book;
        long cancelId;
        AtomicLong nextId = new AtomicLong(1);

        @Setup(Level.Invocation)
        public void placeOrder() {
            book = new OrderBook();
            cancelId = nextId.getAndIncrement();
            book.addOrder(new Order(cancelId, OrderSide.BUY, 100, 10));
        }
    }

    @Benchmark
    public boolean cancelOpenOrder(CancelState s) {
        return s.book.cancelOrder(s.cancelId);
    }

    @State(Scope.Benchmark)
    public static class SnapshotState {
        OrderBook book;
        long levelSeed;

        @Setup(Level.Iteration)
        public void fillBook() {
            book = new OrderBook();
            long id = 1;
            for (long p = 100; p < 150; p++) {
                book.addOrder(new Order(id++, OrderSide.BUY, p, 25));
                book.addOrder(new Order(id++, OrderSide.SELL, p + 50, 25));
            }
            levelSeed = id;
        }
    }

    @Benchmark
    public void snapshotDepth15(SnapshotState s, Blackhole blackhole) {
        blackhole.consume(s.book.snapshot(15));
    }
}
