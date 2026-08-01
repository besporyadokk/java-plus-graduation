package ru.practicum.explore_with_me.comment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {
        "ru.practicum.model.user.client",
        "ru.practicum.model.event.client",
        "ru.practicum.model.request.client"
})
public class CommentServiceStarter {
    public static void main(String[] args) {
        SpringApplication.run(CommentServiceStarter.class, args);
    }
}