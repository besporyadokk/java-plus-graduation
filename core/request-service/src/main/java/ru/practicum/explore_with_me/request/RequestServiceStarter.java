package ru.practicum.explore_with_me.request;

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
public class RequestServiceStarter {
    public static void main(String[] args) {
        SpringApplication.run(RequestServiceStarter.class, args);
    }
}