package ru.practicum.explore_with_me.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {
        "ru.practicum.model.user.client",
        "ru.practicum.model.event.client"

})
public class UserServiceStarter {
    public static void main(String[] args) {
        SpringApplication.run(UserServiceStarter.class, args);
    }
}