package org.karolismed.redisapp.constants;

import java.util.Arrays;

public enum Command {
    REGISTER,
    LOGIN,
    LOGOUT,
    BOOK_LIST,
    LIBRARY_LIST,
    AVAILABLE_BOOKS,
    ADD_LIBRARY,
    TAKE_BOOK,
    RETURN_BOOK,
    MY_BOOKS,
    INSERT_BOOK,
    ADD_BOOK,
    EXIT;

    public static Command fromString(String str) {
        return Arrays.stream(Command.values())
            .filter(command -> command.toString().equalsIgnoreCase(str))
            .findFirst()
            .orElse(null);
    }
}
