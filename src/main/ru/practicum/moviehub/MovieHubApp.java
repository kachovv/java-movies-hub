package ru.practicum.moviehub;

import ru.practicum.moviehub.http.MoviesServer;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;

public class MovieHubApp {
    public static void main(String[] args) throws IOException {
        MoviesStore store = new MoviesStore();
        int port = 8080;
        MoviesServer server;
        try {
            server = new MoviesServer(store, port);
        } catch (IOException e) {
            System.err.println("Не удалось запустить сервер на порту " + port);
            e.printStackTrace();
            return;
        }
        server.start();
        System.out.println("Сервер запущен на http://localhost:" + port);
    }
}