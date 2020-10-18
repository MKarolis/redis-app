package org.karolismed.redisapp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.karolismed.redisapp.constants.Authority;

@Builder
@AllArgsConstructor
@Getter
public class User {
    private int id;
    private Authority authority;
}
