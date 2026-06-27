package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class BaseHttpHandler implements HttpHandler {
    protected static final Gson gson = new Gson();

    @Override
    public abstract void handle(HttpExchange exchange) throws IOException;


    protected String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    protected void sendResponse(HttpExchange exchange, int statusCode, Object data) throws IOException {
        String json = gson.toJson(data);
        sendResponse(exchange, statusCode, json);
    }

    protected void sendResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    protected void sendError(HttpExchange exchange, int statusCode, String error, List<String> details) throws IOException {
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("error", error);
        errorBody.put("details", details);
        sendResponse(exchange, statusCode, errorBody);
    }

    protected void sendNoContent(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(204, -1);
    }

    protected boolean isValidJson(String json) {
        try {
            JsonReader reader = new JsonReader(new StringReader(json));
            reader.setLenient(false);
            JsonParser.parseReader(reader);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}


