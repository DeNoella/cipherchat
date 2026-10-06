package com.cipherchat.controller;

import com.cipherchat.dto.ChangePasswordRequest;
import com.cipherchat.dto.UserProfileResponse;
import com.cipherchat.dto.UserSummary;
import com.cipherchat.model.User;
import com.cipherchat.security.AuthUser;
import com.cipherchat.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Users", description = "Your account and finding people to chat with")
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

    @Operation(summary = "Your own account: username, creation date and key status")
    @GetMapping("/me")
    public UserProfileResponse me(@AuthenticationPrincipal AuthUser me) {
        return userService.profile(me.id());
    }

    @Operation(summary = "Change your login password (your key passphrase is not affected)")
    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AuthUser me,
                               @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(me.id(), request);
    }

    @Operation(summary = "Look up one user by username")
    @GetMapping("/{username}")
    public UserSummary get(@PathVariable
                           @Pattern(regexp = User.USERNAME_REGEX, message = "3-32 characters: letters, digits or underscore")
                           String username) {
        return userService.summary(username);
    }
}
