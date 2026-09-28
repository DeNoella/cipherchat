package com.cipherchat.controller;

import com.cipherchat.dto.UserSummary;
import com.cipherchat.security.AuthUser;
import com.cipherchat.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Users", description = "Find people to chat with")
@Validated
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Search users by username prefix (excludes yourself, max 20 results)")
    @GetMapping
    public List<UserSummary> search(@AuthenticationPrincipal AuthUser me,
                                    @RequestParam("query") @NotBlank
                                    @Pattern(regexp = "^[A-Za-z0-9_]{1,32}$", message = "letters, digits or underscore")
                                    String query) {
        return userService.search(query, me.username());
    }
}
