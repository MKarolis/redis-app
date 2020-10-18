package org.karolismed.redisapp;

import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        System.out.println("Redis Application started");
        RedisApp redisApp = new RedisApp();

        try {
            redisApp.run();
        } catch (IOException e) {
            System.out.println("ERROR: " + e.getMessage());
        }
    }
}
