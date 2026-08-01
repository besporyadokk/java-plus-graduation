package ru.practicum.model.user.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import ru.practicum.model.user.dto.UserShortDto;

import java.util.List;

@FeignClient(name = "user-service", path = "/admin/users")
public interface UserServiceClient {
    @GetMapping("/client/{userId}")
    UserShortDto getUserShortDtoClientById(@PathVariable Long userId);

    @GetMapping("/client/exist/{userId}")
    void validateUserExistingById(@PathVariable Long userId);

    @GetMapping("/client/list")
    List<UserShortDto> getUsersShortByIds(@RequestParam("ids") List<Long> ids);
}