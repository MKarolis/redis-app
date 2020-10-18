package org.karolismed.redisapp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import redis.clients.jedis.Response;

@Builder
@AllArgsConstructor
@Getter
public class PipelinedBookResponse {
    int id;
    Response<String> title;
    Response<String> author;

    int count;
}
