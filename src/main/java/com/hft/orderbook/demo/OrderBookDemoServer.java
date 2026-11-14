package com.hft.orderbook.demo;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;

import com.hft.orderbook.book.OrderBook;
import com.hft.orderbook.matching.MatchResult;
import com.hft.orderbook.matching.MatchingEngine;
import com.hft.orderbook.matching.Trade;
import com.hft.orderbook.model.Order;
import com.hft.orderbook.model.OrderSide;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Demo HTTP server: static UI under {@code /front} and REST API for the in-memory book.
 * Run: {@code mvn -q exec:java}
 */
public final class OrderBookDemoServer {

    private static final int DEFAULT_PORT = 8765;
    private static final int DEFAULT_DEPTH = 15;

    private final Object bookLock = new Object();
    private OrderBook book = new OrderBook();
    private MatchingEngine engine = new MatchingEngine(book);
    private final List<Trade> tradeLog = new ArrayList<>();
    private final AtomicLong nextOrderId = new AtomicLong(1);
    private final ObjectMapper json = new ObjectMapper();

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        new OrderBookDemoServer().start(port);
    }

    private void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        server.createContext("/", new StaticHandler());
        server.createContext("/css/", new StaticHandler());
        server.createContext("/js/", new StaticHandler());
        server.createContext("/api/book", new BookHandler());
        server.createContext("/api/order", new OrderHandler());
        server.createContext("/api/order/status", new OrderStatusHandler());
        server.createContext("/api/replace", new ReplaceHandler());
        server.createContext("/api/cancel", new CancelHandler());
        server.createContext("/api/seed", new SeedHandler());
        server.createContext("/api/trades", new TradesHandler());

        server.start();
        System.out.println("Order book demo: http://localhost:" + port + "/");
    }

    private void seedSampleBook() {
        book = new OrderBook();
        engine = new MatchingEngine(book);
        tradeLog.clear();
        nextOrderId.set(1);

        addOrder(OrderSide.BUY, 102, 50);
        addOrder(OrderSide.BUY, 101, 30);
        addOrder(OrderSide.BUY, 100, 80);
        addOrder(OrderSide.BUY, 100, 25);
        addOrder(OrderSide.BUY, 99, 40);

        addOrder(OrderSide.SELL, 103, 35);
        addOrder(OrderSide.SELL, 104, 60);
        addOrder(OrderSide.SELL, 105, 20);
        addOrder(OrderSide.SELL, 105, 45);
        addOrder(OrderSide.SELL, 107, 70);
    }

    private long addOrder(OrderSide side, long price, long quantity) {
        long id = nextOrderId.getAndIncrement();
        MatchResult result = engine.submit(new Order(id, side, price, quantity));
        tradeLog.addAll(result.trades());
        return id;
    }

    private final class BookHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }
            int depth = parseDepth(exchange.getRequestURI().getQuery());
            String body;
            synchronized (bookLock) {
                body = json.writeValueAsString(book.snapshot(depth));
            }
            sendJson(exchange, 200, body);
        }
    }

    private final class OrderHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }
            JsonNode node = json.readTree(exchange.getRequestBody());
            OrderSide side = OrderSide.valueOf(node.get("side").asText().toUpperCase());
            long price = node.get("price").asLong();
            long quantity = node.get("quantity").asLong();

            long id;
            MatchResult result;
            synchronized (bookLock) {
                if (node.hasNonNull("id")) {
                    id = node.get("id").asLong();
                    nextOrderId.updateAndGet(current -> Math.max(current, id + 1));
                    result = engine.submit(new Order(id, side, price, quantity));
                } else {
                    id = nextOrderId.getAndIncrement();
                    result = engine.submit(new Order(id, side, price, quantity));
                }
                tradeLog.addAll(result.trades());
            }
            var response = json.createObjectNode();
            response.put("id", id);
            response.put("filled", result.filledQuantity());
            response.put("remaining", result.remainingQuantity());
            response.put("resting", result.restingOnBook());
            response.put("status", result.status().name());
            response.set("trades", json.valueToTree(result.trades()));
            sendJson(exchange, 201, json.writeValueAsString(response));
        }
    }

    private final class TradesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }
            String body;
            synchronized (bookLock) {
                body = json.writeValueAsString(tradeLog);
            }
            sendJson(exchange, 200, body);
        }
    }

    private final class OrderStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }
            Long id = parseLongQuery(exchange.getRequestURI().getQuery(), "id");
            if (id == null) {
                sendError(exchange, 400, "Missing query: id");
                return;
            }
            String body;
            synchronized (bookLock) {
                Order order = book.getOrderById(id);
                if (order == null) {
                    sendJson(exchange, 404, "{\"found\":false}");
                    return;
                }
                ObjectNode view = orderToJson(order);
                view.put("found", true);
                body = json.writeValueAsString(view);
            }
            sendJson(exchange, 200, body);
        }
    }

    private final class ReplaceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }
            JsonNode node = json.readTree(exchange.getRequestBody());
            long openOrderId = node.get("openOrderId").asLong();
            long newPrice = node.get("price").asLong();
            long newQuantity = node.get("quantity").asLong();
            long newOrderId;
            MatchResult result;
            synchronized (bookLock) {
                if (node.hasNonNull("newOrderId")) {
                    newOrderId = node.get("newOrderId").asLong();
                    nextOrderId.updateAndGet(current -> Math.max(current, newOrderId + 1));
                } else {
                    newOrderId = nextOrderId.getAndIncrement();
                }
                result = engine.replace(openOrderId, newOrderId, newPrice, newQuantity);
                tradeLog.addAll(result.trades());
            }
            ObjectNode response = json.createObjectNode();
            response.put("openOrderId", openOrderId);
            response.put("newOrderId", newOrderId);
            response.put("filled", result.filledQuantity());
            response.put("remaining", result.remainingQuantity());
            response.put("resting", result.restingOnBook());
            response.put("status", result.status().name());
            response.set("trades", json.valueToTree(result.trades()));
            sendJson(exchange, 200, json.writeValueAsString(response));
        }
    }

    private final class CancelHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }
            JsonNode node = json.readTree(exchange.getRequestBody());
            long id = node.get("id").asLong();
            boolean ok;
            String status = null;
            synchronized (bookLock) {
                ok = book.cancelOrder(id);
                if (ok) {
                    status = book.getOrderById(id).getStatus().name();
                }
            }
            ObjectNode response = json.createObjectNode();
            response.put("cancelled", ok);
            if (status != null) {
                response.put("status", status);
            }
            sendJson(exchange, ok ? 200 : 404, json.writeValueAsString(response));
        }
    }

    private ObjectNode orderToJson(Order order) {
        ObjectNode node = json.createObjectNode();
        node.put("id", order.getId());
        node.put("side", order.getSide().name());
        node.put("price", order.getPrice());
        node.put("quantity", order.getQuantity());
        node.put("remainingQuantity", order.getRemainingQuantity());
        node.put("filledQuantity", order.getQuantity() - order.getRemainingQuantity());
        node.put("status", order.getStatus().name());
        node.put("open", book.isOpen(order.getId()));
        return node;
    }

    private static Long parseLongQuery(String query, String key) {
        if (query == null) {
            return null;
        }
        String prefix = key + "=";
        for (String part : query.split("&")) {
            if (part.startsWith(prefix)) {
                return Long.parseLong(part.substring(prefix.length()));
            }
        }
        return null;
    }

    private final class SeedHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }
            synchronized (bookLock) {
                seedSampleBook();
            }
            sendJson(exchange, 200, "{\"seeded\":true}");
        }
    }

    private final class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) {
                path = "/index.html";
            }
            String resource = "/front" + path;
            byte[] bytes = loadResource(resource);
            if (bytes == null) {
                sendError(exchange, 404, "Not Found");
                return;
            }
            String contentType = contentTypeFor(path);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }

    private static byte[] loadResource(String classpathPath) throws IOException {
        try (var in = OrderBookDemoServer.class.getResourceAsStream(classpathPath)) {
            if (in == null) {
                return null;
            }
            return in.readAllBytes();
        }
    }

    private static String contentTypeFor(String path) {
        if (path.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (path.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (path.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        return "application/octet-stream";
    }

    private static int parseDepth(String query) {
        if (query == null) {
            return DEFAULT_DEPTH;
        }
        for (String part : query.split("&")) {
            if (part.startsWith("depth=")) {
                return Integer.parseInt(part.substring(6));
            }
        }
        return DEFAULT_DEPTH;
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
