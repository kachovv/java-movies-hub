package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

public class MoviesHandler extends BaseHttpHandler {
    private final MoviesStore store;

    public MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getQuery();

        try {
            if (method.equals("GET") && path.equals("/movies")) {
                List<Movie> movies = store.getAllMovies();

                if (query != null && query.startsWith("year=")) {
                    try {
                        int year = Integer.parseInt(query.substring(5));
                        movies = movies.stream()
                                .filter(m -> m.getYear() == year)
                                .toList();
                    } catch (NumberFormatException e) {
                        sendError(exchange, 400, "Неверный формат года",
                                List.of("Параметр year должен быть числом"));
                        return;
                    }
                }
                sendResponse(exchange, 200, movies);
                return;
            }

            if (method.equals("POST") && path.equals("/movies")) {
                // проверка Content-Type
                String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                if (contentType == null || !contentType.startsWith("application/json")) {
                    sendError(exchange, 415, "Неподдерживаемый Content-Type",
                            List.of("Ожидается application/json"));
                    return;
                }

                String body = readBody(exchange);

                if (!isValidJson(body)) {
                    sendError(exchange, 400, "Некорректный JSON",
                            List.of("Невалидный синтаксис JSON"));
                    return;
                }
                Movie movie;
                try {
                    movie = gson.fromJson(body, Movie.class);
                } catch (Exception e) {
                    sendError(exchange, 400, "Некорректный JSON",
                            List.of("Не удалось разобрать JSON: " + e.getMessage()));
                    return;
                }

                List<String> errors = new ArrayList<>();
                String title = movie.getTitle();
                if (title == null || title.isBlank()) {
                    errors.add("Название не должно быть пустым");
                } else if (title.length() > 100) {
                    errors.add("Название не должно превышать 100 символов");
                }

                int year = movie.getYear();
                int currentYear = Year.now().getValue();
                if (year < 1888 || year > currentYear + 1) {
                    errors.add("Год должен быть между 1888 и " + (currentYear + 1));
                }

                if (!errors.isEmpty()) {
                    sendError(exchange, 422, "Ошибка валидации", errors);
                    return;
                }

                Movie added = store.addMovie(movie);
                sendResponse(exchange, 201, added);
                return;
            }

            if (path.startsWith("/movies/") && path.length() > "/movies/".length()) {
                String idStr = path.substring(path.lastIndexOf('/') + 1);
                long id;
                try {
                    id = Long.parseLong(idStr);
                } catch (NumberFormatException e) {
                    sendError(exchange, 400, "ID должен быть числом", List.of());
                    return;
                }

                if (method.equals("GET")) {
                    Movie movie = store.getMovieById(id);
                    if (movie == null) {
                        sendError(exchange, 404, "Фильм не найден",
                                List.of("Фильм с ID " + id + " не существует"));
                    } else {
                        sendResponse(exchange, 200, movie);
                    }
                    return;
                }

                if (method.equals("DELETE")) {
                    boolean deleted = store.deleteMovie(id);
                    if (!deleted) {
                        sendError(exchange, 404, "Фильм не найден",
                                List.of("Фильм с ID " + id + " не существует"));
                    } else {
                        sendNoContent(exchange);
                    }
                    return;
                }
            }

            sendError(exchange, 405, "Метод не разрешён",
                    List.of("Разрешены: GET, POST, DELETE"));

        } catch (Exception e) {
            e.printStackTrace();
            sendError(exchange, 500, "Внутренняя ошибка сервера",
                    List.of(e.getMessage()));
        } finally {
            exchange.close();
        }
    }
}