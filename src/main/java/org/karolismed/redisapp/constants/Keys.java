package org.karolismed.redisapp.constants;

public class Keys {
    public static final String DELIMITER = "||";

    public static final String TICKER_USER = "ticker||user";
    public static final String TICKER_BOOK = "ticker||book";
    public static final String TICKER_LIBRARY = "ticker||library";

    public static final String TEMPLATE_USER = "user||%s";
    public static final String TEMPLATE_BOOK = "book||%s";
    public static final String TEMPLATE_LIBRARY = "library||%s";

    public static final String TEMPLATE_BOOKCOUNT = "bookcount||%s";
    public static final String TEMPLATE_BOOKS_TAKEN = "bookstaken||%s";

    public static final String TEMPLATE_LOOKUP_USER = "lookup||user||%s";
}
