package org.karolismed.redisapp.constants;

import java.util.Arrays;

public enum Authority {
    USER, ADMIN;

    public static Authority fromString(String str) {
        return Arrays.stream(Authority.values())
            .filter(authority -> authority.toString().equalsIgnoreCase(str))
            .findFirst()
            .orElse(null);
    }
}
