package ru.practicum.explore_with_me.event;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {
        "ru.practicum.model.user.client",
        "ru.practicum.model.category.client",
        "ru.practicum.model.request.client"
})
public class EventServiceStarter {
    public static void main(String[] args) {
        SpringApplication.run(EventServiceStarter.class, args);
    }
}