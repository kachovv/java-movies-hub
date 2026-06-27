package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MoviesStore {
    private final Map<Long, Movie> movies = new HashMap<>();
    private long nextId = 1;

    public synchronized Movie addMovie(Movie movie) {
        long id = nextId++;
        movie.setId(id);
        movies.put(id, movie);
        return movie;
    }

    public synchronized Movie getMovieById(long id) {
        return movies.get(id);
    }

    public synchronized List<Movie> getAllMovies() {
        return new ArrayList<>(movies.values());
    }

    public synchronized boolean deleteMovie(long id) {
        return movies.remove(id) != null;
    }
}