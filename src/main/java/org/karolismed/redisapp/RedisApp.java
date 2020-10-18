package org.karolismed.redisapp;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.types.Commandline;
import org.karolismed.redisapp.constants.Authority;
import org.karolismed.redisapp.exception.FailedTransactionException;
import org.karolismed.redisapp.exception.IllegalOperationException;
import org.karolismed.redisapp.jcommander.CLParams;
import org.karolismed.redisapp.model.PipelinedBookResponse;
import org.karolismed.redisapp.model.User;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;
import redis.clients.jedis.Transaction;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.karolismed.redisapp.constants.Keys.TEMPLATE_BOOK;
import static org.karolismed.redisapp.constants.Keys.TEMPLATE_BOOKCOUNT;
import static org.karolismed.redisapp.constants.Keys.TEMPLATE_BOOKS_TAKEN;
import static org.karolismed.redisapp.constants.Keys.TEMPLATE_LIBRARY;
import static org.karolismed.redisapp.constants.Keys.TEMPLATE_LOOKUP_USER;
import static org.karolismed.redisapp.constants.Keys.TEMPLATE_USER;
import static org.karolismed.redisapp.constants.Keys.TICKER_BOOK;
import static org.karolismed.redisapp.constants.Keys.TICKER_LIBRARY;
import static org.karolismed.redisapp.constants.Keys.TICKER_USER;

public class RedisApp {

    private final Jedis jedis;
    private User activeUser;


    public RedisApp() {
        jedis = new Jedis(); // Use default port 6379
    }

    public void run() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            CLParams params = new CLParams();
            String input = reader.readLine();

            try {
                JCommander.newBuilder()
                    .args(Commandline.translateCommandline(input))
                    .addObject(params)
                    .build();

                switch (params.getCommand()) {
                    case REGISTER: {
                        registerUser(params);
                        break;
                    }
                    case LOGIN: {
                        loginUser(params);
                        break;
                    }
                    case BOOK_LIST: {
                        getBookList();
                        break;
                    }
                    case LIBRARY_LIST: {
                        getLibraryList();
                        break;
                    }
                    case LOGOUT: {
                        logoutUser();
                        break;
                    }
                    case ADD_LIBRARY: {
                        addLibrary(params);
                        break;
                    }
                    case INSERT_BOOK: {
                        insertBook(params);
                        break;
                    }
                    case ADD_BOOK: {
                        addBook(params);
                        break;
                    }
                    case TAKE_BOOK: {
                        takeBook(params);
                        break;
                    }
                    case RETURN_BOOK: {
                        returnBook(params);
                        break;
                    }
                    case AVAILABLE_BOOKS: {
                        getAvailableBooks(params);
                        break;
                    }
                    case MY_BOOKS: {
                        getMyBooks();
                        break;
                    }
                    case EXIT: {
                        return;
                    }
                }
            } catch (ParameterException | BuildException e) {
                System.out.println("Invalid Input: " + e.getMessage());
            } catch (IllegalOperationException e) {
                System.out.println("Invalid Operation: " + e.getMessage());
            }  catch (FailedTransactionException e) {
                System.out.println(e.getMessage() + " Please try again");
            }
        }
    }

    private void getMyBooks() {
        validateUserLoggedIn();
        String takenBooksKey = String.format(TEMPLATE_BOOKS_TAKEN, activeUser.getId());

        Map<String, String> takenBookMap = jedis.hgetAll(takenBooksKey);

        List<PipelinedBookResponse> pipelinedBookResponseList = new ArrayList<>();
        Pipeline pipeline = jedis.pipelined();

        takenBookMap.forEach((key, value) -> pipelinedBookResponseList.add(
            PipelinedBookResponse.builder()
                .id(Integer.parseInt(key))
                .count(Integer.parseInt(value))
                .author(pipeline.hget(String.format(TEMPLATE_BOOK, key), "author"))
                .title(pipeline.hget(String.format(TEMPLATE_BOOK, key), "title"))
                .build()
        ));

        pipeline.sync();

        pipelinedBookResponseList.sort(Comparator.comparingInt(PipelinedBookResponse::getId));

        System.out.println("Present book List:");
        System.out.println(String.format("%-8s %-20s %-20s %-8s", "id", "title", "author", "count"));
        System.out.println("-------------------------------------------------------");
        pipelinedBookResponseList.forEach(book -> {
            System.out.println(
                String.format(
                    "%-8s %-20s %-20s %-8s",
                    book.getId(),
                    extractFromResponse(book.getTitle()),
                    extractFromResponse(book.getAuthor()),
                    book.getCount()
                )
            );
        });
        System.out.println("-------------------------------------------------------");
    }

    private void returnBook(CLParams params) {
        validateUserLoggedIn();
        validateParameterPresent(params.getLibraryId(), "Library Id [-lid]");
        validateParameterPresent(params.getBookId(), "Book Id [-bid]");

        int libId = params.getLibraryId();
        int bookId = params.getBookId();

        validateBookId(bookId);
        validateLibraryId(libId);

        String takenBooksKey = String.format(TEMPLATE_BOOKS_TAKEN, activeUser.getId());
        String libBookCountKey = String.format(TEMPLATE_BOOKCOUNT, libId);

        int instancesBorrowed =
            Optional.ofNullable(jedis.hget(takenBooksKey, String.valueOf(bookId)))
                .map(Integer::valueOf)
                .orElseThrow(() ->
                    new IllegalOperationException(String.format("You don't have book %s", bookId))
                );

        jedis.watch(takenBooksKey);
        Transaction transaction = jedis.multi();

        transaction.hincrBy(libBookCountKey, String.valueOf(bookId), 1);
        if (instancesBorrowed > 1) {
            transaction.hincrBy(takenBooksKey, String.valueOf(bookId), -1);
        } else {
            transaction.hdel(takenBooksKey, String.valueOf(bookId));
        }
        pauseExecutionWithMessage("Returning book...");
        executeTransaction(transaction);
        System.out.println("Book successfully returned");
    }

    private void takeBook(CLParams params) {
        validateUserLoggedIn();
        validateParameterPresent(params.getLibraryId(), "Library Id [-lid]");
        validateParameterPresent(params.getBookId(), "Book Id [-bid]");

        int libId = params.getLibraryId();
        int bookId = params.getBookId();

        validateBookExists(bookId, libId);
        String takenBooksKey = String.format(TEMPLATE_BOOKS_TAKEN, activeUser.getId());
        String libBookCountKey = String.format(TEMPLATE_BOOKCOUNT, libId);

        jedis.watch(takenBooksKey, libBookCountKey);

        int bookCount = Integer.parseInt(jedis.hget(libBookCountKey, String.valueOf(bookId)));

        Transaction transaction = jedis.multi();
        transaction.hincrBy(takenBooksKey, String.valueOf(bookId), 1);

        if (bookCount > 1) {
            transaction.hincrBy(libBookCountKey, String.valueOf(bookId), -1);
        } else {
            transaction.hdel(libBookCountKey, String.valueOf(bookId));
        }

        pauseExecutionWithMessage("Taking book...");
        executeTransaction(transaction);
        System.out.println("Book taken successfully");
    }

    private void getBookList() {
        int bookCount = Integer.parseInt(jedis.get(TICKER_BOOK));
        List<PipelinedBookResponse> pipelinedBookResponseList = new ArrayList<>();

        Pipeline pipeline = jedis.pipelined();
        for (int i = 1; i <= bookCount; i++) {
            String bookKey = String.format(TEMPLATE_BOOK, i);
            pipelinedBookResponseList.add(
                PipelinedBookResponse.builder()
                    .id(i)
                    .title(pipeline.hget(bookKey, "title"))
                    .author(pipeline.hget(bookKey, "author"))
                    .build()
            );
        }
        pipeline.sync();

        System.out.println("Book List:");
        System.out.println(String.format("%-8s %-20s %-20s", "id", "title", "author"));
        System.out.println("-------------------------------------------------------");
        for (int i = 1; i <= bookCount; i++) {
            PipelinedBookResponse book = pipelinedBookResponseList.get(i - 1);
            System.out.println(
                String.format(
                    "%-8s %-20s %-20s",
                    i,
                    extractFromResponse(book.getTitle()),
                    extractFromResponse(book.getAuthor())
                )
            );
        }
        System.out.println("-------------------------------------------------------");
    }

    private void getLibraryList() {
        int libraryCount = Integer.parseInt(jedis.get(TICKER_LIBRARY));
        List<Response<String>> libraryResponses = new ArrayList<>();

        Pipeline pipeline = jedis.pipelined();
        for (int i = 1; i <= libraryCount; i++) {
            String bookKey = String.format(TEMPLATE_LIBRARY, i);
            libraryResponses.add(
                pipeline.hget(bookKey, "city")
            );
        }
        pipeline.sync();

        System.out.println("Library List:");
        System.out.println(String.format("%-8s %-20s", "id", "city"));
        System.out.println("----------------------------------------");
        for (int i = 1; i <= libraryCount; i++) {
            System.out.println(
                String.format(
                    "%-8s %-20s",
                    i,
                    extractFromResponse(libraryResponses.get(i - 1))
                )
            );
        }
        System.out.println("----------------------------------------");
    }

    private void getAvailableBooks(CLParams params) {
        validateParameterPresent(params.getLibraryId(), "Library Id [-lid]");
        validateLibraryId(params.getLibraryId());

        Map<String, String> presentBooks =
            jedis.hgetAll(String.format(TEMPLATE_BOOKCOUNT, params.getLibraryId()));

        List<PipelinedBookResponse> pipelinedBookResponseList = new ArrayList<>();
        Pipeline pipeline = jedis.pipelined();

        presentBooks.forEach((key, value) -> {
            String bookKey = String.format(TEMPLATE_BOOK, key);
            pipelinedBookResponseList.add(
                PipelinedBookResponse.builder()
                    .id(Integer.parseInt(key))
                    .title(pipeline.hget(bookKey, "title"))
                    .author(pipeline.hget(bookKey, "author"))
                    .count(Integer.parseInt(value))
                    .build()
            );
        });

        pipeline.sync();

        System.out.println("Present book List:");
        System.out.println(String.format("%-8s %-20s %-20s %-8s", "id", "title", "author", "count"));
        System.out.println("-------------------------------------------------------");
        pipelinedBookResponseList.sort(Comparator.comparingInt(PipelinedBookResponse::getId));
        pipelinedBookResponseList.forEach(book -> {
            System.out.println(
                String.format(
                    "%-8s %-20s %-20s %-8s",
                    book.getId(),
                    extractFromResponse(book.getTitle()),
                    extractFromResponse(book.getAuthor()),
                    book.getCount()
                )
            );
        });
        System.out.println("-------------------------------------------------------");
    }

    private void addBook(CLParams params) {
        validateUserHasAuthority(Authority.ADMIN);
        validateParameterPresent(params.getLibraryId(), "Library Id [-lid]");
        validateParameterPresent(params.getBookId(), "Book Id [-bid]");

        validateBookId(params.getBookId());
        validateLibraryId(params.getLibraryId());

        jedis.watch(String.format(TEMPLATE_BOOKCOUNT, params.getLibraryId()));
        Transaction transaction = jedis.multi();
        transaction.hincrBy(
            String.format(TEMPLATE_BOOKCOUNT, params.getLibraryId()),
            String.valueOf(params.getBookId()),
            1
        );

        pauseExecutionWithMessage("Adding book...");
        executeTransaction(transaction);
        System.out.println("Book added successfully");
    }

    private void addLibrary(CLParams params) {
        validateUserHasAuthority(Authority.ADMIN);
        validateParameterPresent(params.getCity(), "City [-c]");

        String city = params.getCity().trim();

        jedis.watch(TICKER_LIBRARY, TICKER_BOOK);
        int newLibraryId = Integer.parseInt(jedis.get(TICKER_LIBRARY)) + 1;

        Transaction transaction = jedis.multi();
        transaction.hset(String.format(TEMPLATE_LIBRARY, newLibraryId), Map.of(
           "city", city
        ));

        pauseExecutionWithMessage("Adding library...");

        transaction.incr(TICKER_LIBRARY);
        executeTransaction(transaction);

        System.out.println("Library added successfully");
    }

    private void insertBook(CLParams params) {
        validateUserHasAuthority(Authority.ADMIN);
        validateParameterPresent(params.getTitle(), "Title [-t]");
        validateParameterPresent(params.getAuthor(), "Author [-a]");

        String title = params.getTitle().trim();
        String author = params.getAuthor().trim();

        jedis.watch(TICKER_BOOK);
        int newBookId = Integer.parseInt(jedis.get(TICKER_BOOK)) + 1;

        Transaction transaction = jedis.multi();
        transaction.hset(String.format(TEMPLATE_BOOK, newBookId), Map.of(
           "title", title,
           "author", author
        ));

        pauseExecutionWithMessage("Inserting book...");

        transaction.incr(TICKER_BOOK);
        executeTransaction(transaction);

        System.out.println("Book Added successfully");
    }

    private void logoutUser() {
        if (isNull(activeUser)) {
            System.out.println("Try logging in first :)");
            return;
        }
        activeUser = null;
        System.out.println("Logged out successfully");
    }

    private void loginUser(CLParams params) {
        validateParameterPresent(params.getUserName(), "Username [-u]");
        validateParameterPresent(params.getPassword(), "Password [-pw]");

        String trimmedUsername = params.getUserName().trim();
        String userId = Optional.ofNullable(
            jedis.get(String.format(TEMPLATE_LOOKUP_USER, trimmedUsername))
        ).orElseThrow(() -> new IllegalOperationException("Password or username incorrect"));

        String passwordHex = DigestUtils.sha256Hex(params.getPassword());

        if (!jedis.hget(String.format(TEMPLATE_USER, userId), "password")
            .equals(passwordHex)
        ) {
            throw new IllegalOperationException("Password or username incorrect");
        }

        activeUser = User.builder()
            .id(Integer.parseInt(userId))
            .authority(Authority.fromString(
                jedis.hget(String.format(TEMPLATE_USER, userId), "authority"))
            ).build();
        System.out.println("Successfully logged in as " + trimmedUsername);
    }

    private void registerUser(CLParams params) {
        validateParameterPresent(params.getUserName(), "Username [-u]");
        validateParameterPresent(params.getPassword(), "Password [-pw]");

        String trimmedUsername = params.getUserName().trim();
        String passwordHex = DigestUtils.sha256Hex(params.getPassword());

        if (nonNull(jedis.get(String.format(TEMPLATE_LOOKUP_USER, trimmedUsername)))) {
            throw new IllegalOperationException(
                String.format("User %s already exists", trimmedUsername)
            );
        }

        jedis.watch(TICKER_USER);
        int newUserId = Integer.parseInt(jedis.get(TICKER_USER)) + 1;
        Transaction transaction = jedis.multi();

        pauseExecutionWithMessage("Creating user account...");

        transaction.hset(String.format(TEMPLATE_USER, newUserId), Map.of(
            "name", trimmedUsername,
            "password", passwordHex,
            "authority", Authority.USER.toString()
        ));
        transaction.incr(TICKER_USER);
        transaction.set(
            String.format(TEMPLATE_LOOKUP_USER, params.getUserName().trim()),
            String.valueOf(newUserId)
        );
        executeTransaction(transaction);

        activeUser = User.builder()
            .id(newUserId)
            .authority(Authority.USER)
            .build();
        System.out.println("Successfully logged in as " + params.getUserName());
    }

    private void validateParameterPresent(Object obj, String parameterName) {
        if (isNull(obj) ||
            (obj instanceof String && ((String) obj).trim().isEmpty())
        ) {
            throw new ParameterException(String.format("%s not provided", parameterName));
        }
    }

    private void pauseExecutionWithMessage(String message) {
        System.out.print(message + " [Enter]");
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            reader.readLine();
        } catch (IOException e) {
            System.out.println("Could not get user's input");
            System.exit(1);
        }
    }

    private void executeTransaction(Transaction transaction) {
        if (isNull(transaction.exec())) {
            throw new FailedTransactionException();
        }
    }

    private String extractFromResponse(Response<String> response) {
        return Optional.ofNullable(response)
            .map(Response::get)
            .orElse("");
    }

    private void validateUserHasAuthority(Authority authority) {
        validateUserLoggedIn();

        if (!authority.equals(activeUser.getAuthority())) {
            throw new IllegalOperationException("Insufficient rights");
        }
    }

    private void validateUserLoggedIn() {
        if (isNull(activeUser)) {
            throw new IllegalOperationException("User not logged in");
        }
    }

    private void validateBookExists(int bookId, int libId) {
        validateBookId(bookId);
        validateLibraryId(libId);

        String response = jedis.hget(
            String.format(TEMPLATE_BOOKCOUNT, libId), String.valueOf(bookId)
        );
        if (isNull(response)) {
            throw new IllegalOperationException(
                String.format("Book %s is not present in library %s", bookId, libId)
            );
        }
    }

    private void validateBookId(int bookId) {
        int bookCount = Integer.parseInt(jedis.get(TICKER_BOOK));

        if (bookId > bookCount || bookId < 1) {
            throw new IllegalOperationException(
                String.format("No book with id %s", bookId)
            );
        }
    }

    private void validateLibraryId(int libraryId) {
        int libCount = Integer.parseInt(jedis.get(TICKER_LIBRARY));

        if (libraryId > libCount || libraryId < 1) {
            throw new IllegalOperationException(
                String.format("No library with id %s", libraryId)
            );
        }
    }
}
