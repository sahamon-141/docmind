package com.example.docmind.controller;

import com.example.docmind.dto.ApiResponse;
import com.example.docmind.dto.DocumentMetadataDto;
import com.example.docmind.dto.DocumentResponseDto;
import com.example.docmind.security.AuthUser;
import com.example.docmind.service.DocumentMetaDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@Tag(name = "Document Management", description = "Upload, list and delete the authenticated user's documents. Other users' documents always return 404.")
@RequiredArgsConstructor
public class DocumentController {
    private final DocumentMetaDataService documentService;

    //upload a single document
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload and index a document",
            description = "Supported: pdf, txt, md, csv, docx, doc, pptx, odt, rtf, html. The file content is sniffed, not just the extension. "
                    + "400 EMPTY_FILE, 413 FILE_TOO_LARGE, 415 UNSUPPORTED_DOCUMENT, 422 DOCUMENT_PROCESSING_FAILED (the document is kept with status FAILED), "
                    + "502 when embeddings cannot be generated.")
    public ResponseEntity<ApiResponse<DocumentResponseDto>> uploadDocument(@AuthenticationPrincipal AuthUser user,
                                                                           @RequestParam("file") MultipartFile file) {
        DocumentResponseDto result = documentService.uploadAndProcess(user, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result, "Document uploaded and indexed successfully"));
    }

    //upload multiple documents
    @PostMapping(value = "/upload-multiple", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload several documents", description = "Each file is processed independently; failures are reported per item with status FAILED.")
    public ResponseEntity<ApiResponse<List<DocumentResponseDto>>> uploadMultiple(@AuthenticationPrincipal AuthUser user,
                                                                                 @RequestParam("files") List<MultipartFile> files) {
        List<DocumentResponseDto> results = documentService.uploadMultipleDocuments(user, files);
        long failed = results.stream().filter(r -> r.getStatus() == com.example.docmind.entity.DocumentStatus.FAILED).count();
        String message = failed == 0 ? "Documents uploaded successfully" : failed + " of " + results.size() + " documents failed";
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(results, message));
    }

    //list all uploaded documents
    @GetMapping
    @Operation(summary = "List the authenticated user's documents, newest first")
    public ResponseEntity<ApiResponse<List<DocumentMetadataDto>>> getAllDocuments(@AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.ok(ApiResponse.ok(documentService.getAllDocuments(user), null));
    }

    //get document by id
    @GetMapping("/{id}")
    @Operation(summary = "Get one document's metadata")
    public ResponseEntity<ApiResponse<DocumentMetadataDto>> getDocumentById(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(documentService.getDocumentById(user, id), null));
    }

    //delete document by id
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a document and all of its vector chunks")
    public ResponseEntity<ApiResponse<Void>> deleteDocumentById(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        documentService.deleteDocumentById(user, id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Document deleted successfully"));
    }
}
