package com.cipherchat.controller;

import com.cipherchat.dto.AttachmentResponse;
import com.cipherchat.security.AuthUser;
import com.cipherchat.service.AttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Tag(name = "Attachments", description = "Encrypted file blobs (max 10 MB)")
@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @Operation(summary = "Upload an OpenPGP-encrypted file (binary or armored) for a conversation. "
            + "Reference the returned id in POST /api/messages.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentResponse upload(@AuthenticationPrincipal AuthUser me,
                                     @RequestParam("recipientUsername") String recipientUsername,
                                     @RequestPart("file") MultipartFile file) {
        return attachmentService.upload(me.id(), recipientUsername, file);
    }

    @Operation(summary = "Download an encrypted attachment (conversation participants only). Decrypt in the client.")
    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        AttachmentService.Download download = attachmentService.download(me.id(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(download.sizeBytes())
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(id + ".pgp").build().toString())
                .body(download.content());
    }
}
