package com.example.docmind.controller;

import com.example.docmind.dto.ApiResponse;
import com.example.docmind.dto.DocumentMetadataDto;
import com.example.docmind.dto.DocumentResponseDto;
import com.example.docmind.service.DocumentMetaDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@Tag(
        name="Document Management",
        description="Endpoint for uploading and managing documents"
)
@RequiredArgsConstructor
public class DocumentController {
    private final DocumentMetaDataService documentService;

    //upload a single document
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

    //upload multiple documents
    @PostMapping(value="/upload-multiple",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload multiple documents simultaneously",
            description = "This api is used to upload and index multiple document files"
    )
    public ResponseEntity<ApiResponse<List<DocumentResponseDto>>> uploadMultiple(@RequestParam("files")List<MultipartFile> files){
            List<DocumentResponseDto> responseDto = documentService.uploadMultipleDocuments(files);
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.<List<DocumentResponseDto>>
                                        builder()
                                .message("Document uploaded successfully")
                                .success(true)
                                .timestamps(LocalDateTime.now())
                                .data(responseDto)
                                .build()
                );
    }

    //list all uploaded documents
    @GetMapping
    @Operation(
            summary = "Get a list of all uploaded documents",
            description = "This api is used to get all the documents"
    )
    public ResponseEntity<ApiResponse<List<DocumentMetadataDto>>> getAllDocuments(){
        List<DocumentMetadataDto> documents = documentService.getAllDocuments();
        return ResponseEntity.status(HttpStatus.OK).body(
                ApiResponse.<List<DocumentMetadataDto>>
                                builder()
                        .message("Fetching Documents")
                        .success(true)
                        .timestamps(LocalDateTime.now())
                        .data(documents)
                        .build()
        );
    }

    //get document by id
    @GetMapping("/{id}")
    @Operation(summary = "Get metadata of a specific document by id")
    public ResponseEntity<ApiResponse<DocumentMetadataDto>> getDocumentById(@PathVariable UUID id){
        DocumentMetadataDto document = documentService.getDocumentById(id);
        return ResponseEntity.status(HttpStatus.OK).body(
                ApiResponse.<DocumentMetadataDto>builder()
                        .message("Fetching document by id")
                        .success(true)
                        .timestamps(LocalDateTime.now())
                        .data(document)
                        .build()

        );
    }

    //delete document by id
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete metadata of a specific document by id")
    public ResponseEntity<ApiResponse<Void>> deleteDocumentById(@PathVariable UUID id){
        documentService.deleteDocumentById(id);
        return ResponseEntity.status(HttpStatus.OK).body(
                ApiResponse.<Void>builder()
                        .message("Document deleted successfully")
                        .success(true)
                        .timestamps(LocalDateTime.now())
                        .data(null)
                        .build()
        );
    }
}
