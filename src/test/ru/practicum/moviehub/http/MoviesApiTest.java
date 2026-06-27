package ru.practicum.moviehub.http;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Year;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class MoviesApiTest {
    private static final String BASE = "http://localhost:8080";
    private static MoviesServer server;
    private static HttpClient client;
    private static MoviesStore store;

    @BeforeAll
    static void beforeAll() throws IOException {
        store = new MoviesStore();
        server = new MoviesServer(store, 8080);
        server.start();
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    private HttpResponse<String> sendGet() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Accept", "application/json")
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> sendPost(String jsonBody, Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
        // Добавляем заголовки
        headers.forEach(builder::header);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> sendPost(String jsonBody) throws Exception {
        return sendPost(jsonBody, Map.of("Content-Type", "application/json"));
    }

    private HttpResponse<String> sendGetById(String id) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + id))
                .header("Accept", "application/json")
                .GET()
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String extractIdFromPostResponse(HttpResponse<String> response) {
        String body = response.body();
        int start = body.indexOf("\"id\"");
        if (start == -1) throw new RuntimeException("No id field in response");
        start = body.indexOf(":", start);
        if (start == -1) throw new RuntimeException("Malformed id");
        int end = body.indexOf(",", start);
        if (end == -1) end = body.indexOf("}", start);
        if (end == -1) throw new RuntimeException("Malformed JSON");

        return body.substring(start + 1, end).trim();
    }

    private HttpResponse<String> sendDelete(String id) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + id))
                .DELETE()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> sendGetByYear(String year) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=" + year))
                .header("Accept", "application/json")
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private void assertErrorResponse(HttpResponse<String> response) {
        String body = response.body();
        assertTrue(body.contains("\"error\"") || body.contains("error"),
                "Ответ ошибки должен содержать поле 'error'");
    }

    @AfterAll
    static void afterAll() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");
        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");
        String body = resp.body().trim();
        assertTrue(body.startsWith("[") && body.endsWith("]"),
                "Ожидается JSON-массив");
    }

    @Test
    void getMovies_whenMovieExist_returnsListWithAddedMovies() throws Exception {
        String movieJson = """
                {
                "title": "Hobby Games",
                "year": 2000,
                "director": "Karl Lagerfeld"
                }
                """;

        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(movieJson, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> postResponse = client.send(postRequest,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int postStatus = postResponse.statusCode();

        assertTrue(postStatus == 201 || postStatus == 200,
                "POST /movies должен вернуть 201 или 200, а получили " + postStatus);

        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080/movies"))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> getResponse = client.send(getRequest,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, getResponse.statusCode(), "GET /movies должен вернуть 200");

        String body = getResponse.body().trim();
        assertTrue(body.startsWith("[") && body.endsWith("]"),
                "Ответ должен быть JSON-массивом");
        assertTrue(body.contains("Hobby Games"),
                "В списке фильмов должно быть название 'HobbyGames'");
    }

    @Test
    void postMovie_whenValid_returns201AndCreatedMovie() throws Exception {
        String movieJson = """
                {
                    "title": "Dark",
                    "year": 2010,
                    "director": "Nolan"
                }
                """;

        HttpResponse<String> postResp = sendPost(movieJson);
        assertEquals(201, postResp.statusCode(), "POST должен вернуть 201 Created");
        HttpResponse<String> getResp = sendGet();
        assertEquals(200, getResp.statusCode());
        String body = getResp.body();
        assertTrue(body.contains("Dark"), "Фильм должен быть в списке");
    }

    @Test
    void postMovie_whenTitleIsEmpty_returnsBadRequest() throws Exception {
        String movieJson = """
                {
                    "title": "",
                    "year": 2000,
                    "director": "Some"
                }
                """;

        HttpResponse<String> resp = sendPost(movieJson);

        assertTrue(resp.statusCode() == 400 || resp.statusCode() == 422,
                "Ожидается 400 или 422, получено " + resp.statusCode());
    }

    @Test
    void postMovie_whenTitleTooLong_returnsBadRequest() throws Exception {
        String longTitle = "K".repeat(101);
        String movieJson = String.format("""
                {
                    "title": "%s",
                    "year": 2000,
                    "director": "Some"
                }
                """, longTitle);

        HttpResponse<String> resp = sendPost(movieJson);

        assertTrue(resp.statusCode() == 400 || resp.statusCode() == 422,
                "Ожидается 400 или 422, получено " + resp.statusCode());
    }

    @Test
    void postMovie_whenYearTooEarly_returnsBadRequest() throws Exception {
        String movieJson = """
                {
                    "title": "Matrix",
                    "year": 1800,
                    "director": "Unknown"
                }
                """;

        HttpResponse<String> resp = sendPost(movieJson);

        assertTrue(resp.statusCode() == 400 || resp.statusCode() == 422,
                "Ожидается 400 или 422, получено " + resp.statusCode());
    }

    @Test
    void postMovie_whenYearTooFar_returnsBadRequest() throws Exception {
        int futureYear = Year.now().getValue() + 2;
        String movieJson = String.format("""
                {
                    "title": "Back in the Future",
                    "year": %d,
                    "director": "Unknown"
                }
                """, futureYear);

        HttpResponse<String> resp = sendPost(movieJson);

        assertTrue(resp.statusCode() == 400 || resp.statusCode() == 422,
                "Ожидается 400 или 422, получено " + resp.statusCode());
    }

    @Test
    void postMovie_whenWrongContentType_returnsBadRequest() throws Exception {
        String movieJson = "{ \"title\": \"Life\", \"year\": 1999 }";
        HttpResponse<String> resp = sendPost(movieJson, Map.of("Content-Type", "text/plain"));
        // Ожидаем 415 (Unsupported Media Type) или 400
        assertTrue(resp.statusCode() == 415 || resp.statusCode() == 400,
                "Ожидается 415 или 400, получено " + resp.statusCode());
    }

    @Test
    void getMovieById_whenExists_returnsMovie() throws Exception {
        String movieJson = """
                {
                    "title": "The Godfather",
                    "year": 1972,
                    "director": "Coppola"
                }
                """;
        HttpResponse<String> postResp = sendPost(movieJson);
        assertEquals(201, postResp.statusCode());
        String id = extractIdFromPostResponse(postResp);
        HttpResponse<String> getResp = sendGetById(id);
        assertEquals(200, getResp.statusCode());
        String body = getResp.body();
        assertTrue(body.contains("\"title\":\"The Godfather\"") || body.contains("\"title\": \"The Godfather\""),
                "Ответ должен содержать название фильма");
        assertTrue(body.contains("\"year\":1972"), "Ответ должен содержать год");
        assertTrue(body.contains("\"director\":\"Coppola\"") || body.contains("\"director\": \"Coppola\""),
                "Ответ должен содержать режиссёра");
    }

    @Test
    void getMovieById_whenNotFound_returns404() throws Exception {
        HttpResponse<String> resp = sendGetById("99999");
        assertEquals(404, resp.statusCode(), "Ожидается 404 Not Found");
    }

    @Test
    void getMovieById_whenIdIsNotNumber_returnsBadRequest() throws Exception {
        HttpResponse<String> resp = sendGetById("abc");
        assertEquals(400, resp.statusCode(), "Ожидается 400 Bad Request");
    }

    @Test
    void deleteMovie_whenExists_returns204AndDeletesMovie() throws Exception {
        String movieJson = """
                {
                    "title": "Perfection",
                    "year": 1994,
                    "director": "Tarantino"
                }
                """;
        HttpResponse<String> postResp = sendPost(movieJson);
        assertEquals(201, postResp.statusCode());
        String id = extractIdFromPostResponse(postResp);
        HttpResponse<String> deleteResp = sendDelete(id);
        assertTrue(deleteResp.statusCode() == 204 || deleteResp.statusCode() == 200,
                "Ожидается 204 или 200, получено " + deleteResp.statusCode());
        HttpResponse<String> getResp = sendGetById(id);
        assertEquals(404, getResp.statusCode(), "GET /movies/{id} должен вернуть 404 после удаления");
    }

    @Test
    void deleteMovie_whenNotFound_returns404() throws Exception {
        HttpResponse<String> resp = sendDelete("99999");
        assertEquals(404, resp.statusCode(), "Ожидается 404 Not Found");
    }

    @Test
    void deleteMovie_whenIdIsNotNumber_returnsBadRequest() throws Exception {
        HttpResponse<String> resp = sendDelete("abc");
        assertEquals(400, resp.statusCode(), "Ожидается 400 Bad Request");
    }

    @Test
    void getMoviesByYear_whenMoviesExist_returnsMoviesOfThatYear() throws Exception {
        String movie1999 = """
                {
                    "title": "The Matrix",
                    "year": 1999,
                    "director": "Wachowski"
                }
                """;
        sendPost(movie1999);

        String movie2000 = """
                {
                    "title": "Gladiator",
                    "year": 2000,
                    "director": "Scott"
                }
                """;
        sendPost(movie2000);

        HttpResponse<String> resp = sendGetByYear("1999");
        assertEquals(200, resp.statusCode());
        String body = resp.body().trim();
        assertTrue(body.startsWith("[") && body.endsWith("]"),
                "Ответ должен быть JSON-массивом");
        assertTrue(body.contains("The Matrix"),
                "Должен быть фильм 'The Matrix' (1999)");
        assertFalse(body.contains("Gladiator"),
                "Не должно быть фильма 'Gladiator' (2000)");
    }

    @Test
    void getMoviesByYear_whenNoMovies_returnsEmptyArray() throws Exception {
        HttpResponse<String> resp = sendGetByYear("1980");
        assertEquals(200, resp.statusCode());
        String body = resp.body().trim();
        assertEquals("[]", body, "Должен вернуться пустой массив");
    }

    @Test
    void getMoviesByYear_whenYearNotNumber_returnsBadRequest() throws Exception {
        HttpResponse<String> resp = sendGetByYear("abcd");
        assertEquals(400, resp.statusCode(),
                "Ожидается 400 Bad Request для нечислового года");
    }

    @Test
    void unsupportedMethod_returns405() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(405, response.statusCode(), "Ожидается 405 Method Not Allowed");
        assertErrorResponse(response);
    }
}