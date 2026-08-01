package ru.practicum.explore_with_me.category;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {
        "ru.practicum.model.event.client"
})
public class CategoryServiceStarter {
    public static void main(String[] args) {
        SpringApplication.run(CategoryServiceStarter.class, args);
    }
}