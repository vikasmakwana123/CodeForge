package com.example.CodeForge.controller;


import com.example.CodeForge.service.FileService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    // CREATE FILE
    @PostMapping("/file")
    public ResponseEntity<?> createFile(
            @RequestParam String path
    ) {
        try {

            fileService.createFile(path);

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(
                            Map.of(
                                    "message",
                                    "File created successfully",
                                    "path",
                                    path
                            )
                    );

        } catch (Exception e) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            Map.of(
                                    "error",
                                    e.getMessage()
                            )
                    );
        }
    }


    // CREATE FOLDER
    @PostMapping("/folder")
    public ResponseEntity<?> createFolder(
            @RequestParam String path
    ) {

        try {

            fileService.createFolder(path);

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(
                            Map.of(
                                    "message",
                                    "Folder created successfully",
                                    "path",
                                    path
                            )
                    );

        } catch (Exception e) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            Map.of(
                                    "error",
                                    e.getMessage()
                            )
                    );
        }
    }

    // READ FILE
    @GetMapping
    public ResponseEntity<?> readFile(
            @RequestParam String path
    ) {

        try {

            String content =
                    fileService.readFile(path);

            return ResponseEntity.ok(
                    Map.of(
                            "path",
                            path,
                            "content",
                            content
                    )
            );

        } catch (Exception e) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            Map.of(
                                    "error",
                                    e.getMessage()
                            )
                    );
        }
    }

    // WRITE / UPDATE FILE
    @PutMapping
    public ResponseEntity<?> writeFile(
            @RequestParam String path,
            @RequestBody String content
    ) {

        try {

            fileService.writeFile(
                    path,
                    content
            );

            return ResponseEntity.ok(
                    Map.of(
                            "message",
                            "File saved successfully",
                            "path",
                            path
                    )
            );

        } catch (Exception e) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            Map.of(
                                    "error",
                                    e.getMessage()
                            )
                    );
        }
    }

    // DELETE FILE / FOLDER
    @DeleteMapping
    public ResponseEntity<?> delete(
            @RequestParam String path
    ) {

        try {

            fileService.delete(path);

            return ResponseEntity.ok(
                    Map.of(
                            "message",
                            "Deleted successfully",
                            "path",
                            path
                    )
            );

        } catch (Exception e) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            Map.of(
                                    "error",
                                    e.getMessage()
                            )
                    );
        }
    }

     // LIST DIRECTORY
     @GetMapping("/list")
    public ResponseEntity<?> listDirectory(
            @RequestParam(defaultValue = ".")
            String path
    ) {

        try {

            List<String> files =
                    fileService.listDirectory(path);

            return ResponseEntity.ok(
                    files
            );

        } catch (Exception e) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            Map.of(
                                    "error",
                                    e.getMessage()
                            )
                    );
        }
    }


     // CHECK EXISTS
    @GetMapping("/exists")
    public ResponseEntity<?> exists(
            @RequestParam String path
    ) {

        boolean exists =
                fileService.exists(path);

        return ResponseEntity.ok(
                Map.of(
                        "path",
                        path,
                        "exists",
                        exists
                )
        );
    }
}
