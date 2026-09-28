package com.cipherchat.controller;

import com.cipherchat.dto.KeyResponse;
import com.cipherchat.dto.UploadKeyRequest;
import com.cipherchat.model.User;
import com.cipherchat.security.AuthUser;
import com.cipherchat.service.PublicKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Keys", description = "OpenPGP public key directory")
@Validated
@RestController
@RequestMapping("/api/keys")
public class KeyController {

    private final PublicKeyService publicKeyService;

    public KeyController(PublicKeyService publicKeyService) {
        this.publicKeyService = publicKeyService;
    }

    @Operation(summary = "Upload or replace your public key. Validated and fingerprinted server-side.")
    @PutMapping("/me")
    public KeyResponse upload(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody UploadKeyRequest request) {
        return publicKeyService.upload(me.id(), request.publicKey());
    }

    @Operation(summary = "Fetch a user's public key and fingerprint")
    @GetMapping("/{username}")
    public KeyResponse get(@PathVariable @Pattern(regexp = User.USERNAME_REGEX, message = "invalid username")
                           String username) {
        return publicKeyService.get(username);
    }
}
