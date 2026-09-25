package com.example.docmind.controller;

import com.example.docmind.dto.ApiResponse;
import com.example.docmind.dto.DocumentResponseDto;
import com.example.docmind.service.DocumentMetaDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/documents")
@Tag(
        name="Document Management",
        description="Endpoint for uploading and managing documents"
)
@RequiredArgsConstructor
public class DocumentController {
    private final DocumentMetaDataService documentService;

    @PostMapping(value="/upload",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload and index document",
            description = "This api is used to upload and index document files."
    )
    public ResponseEntity<ApiResponse<DocumentResponseDto>> uploadDocument(@RequestParam("file") MultipartFile file){
            //process the file
        DocumentResponseDto documentResponseDto = this.documentService.uploadAndProcess(file);
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<DocumentResponseDto>builder()
                            .success(true)
                            .data(documentResponseDto)
                            .timestamps(LocalDateTime.now())
                            .message("Document uploaded and indexed sucessfully")
                    .build()

            );
    }
}
