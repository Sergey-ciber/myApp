package com.demo.taskmanager.controller;

import com.demo.taskmanager.dto.FileUploadResponse;
import com.demo.taskmanager.service.ImageStorageService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final ImageStorageService imageStorageService;

    public FileController(ImageStorageService imageStorageService) {
        this.imageStorageService = imageStorageService;
    }

    /** Загрузить медиафайл и получить его публичный URL. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileUploadResponse> upload(@RequestParam("file") MultipartFile file) {
        String url = imageStorageService.store(file);
        return ResponseEntity.ok(new FileUploadResponse(url));
    }

    @GetMapping("/{filename}")
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
        Path path = imageStorageService.resolve(filename);
        MediaType type = probeType(filename);
        return ResponseEntity.ok().contentType(type).body(new FileSystemResource(path));
    }

    private MediaType probeType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        return MediaType.IMAGE_PNG;
    }
}
