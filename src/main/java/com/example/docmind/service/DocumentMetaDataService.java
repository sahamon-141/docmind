package com.example.docmind.service;

import com.example.docmind.config.AppProperties;
import com.example.docmind.dto.DocumentMetadataDto;
import com.example.docmind.dto.DocumentResponseDto;
import com.example.docmind.entity.DocumentMetadata;
import com.example.docmind.entity.DocumentStatus;
import com.example.docmind.exception.ApiException;
import com.example.docmind.exception.DocumentProcessingException;
import com.example.docmind.exception.ResourceNotFoundException;
import com.example.docmind.repository.DocumentMetaDataRepo;
import com.example.docmind.repository.UserRepository;
import com.example.docmind.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Owner-scoped document lifecycle. Every read and delete goes through findByIdAndOwnerId, so another
 * user's document behaves exactly like a missing one (404).
 * <p>
 * Uploads are processed synchronously, but not in one transaction: the PROCESSING row is committed first,
 * the slow embedding call runs outside any transaction, and the final INDEXED/FAILED status is committed
 * afterwards. A failed upload therefore stays visible in the library with its error message.
 */
@Service
@RequiredArgsConstructor
public class DocumentMetaDataService {
    private static final Logger log = LoggerFactory.getLogger(DocumentMetaDataService.class);
    private static final String NOT_FOUND = "Document not found";

    private final DocumentMetaDataRepo documentRepo;
    private final UserRepository userRepository;
    private final DocumentValidationService validationService;
    private final DocumentParserService parserService;
    private final DocumentIngestionService ingestionService;
    private final VectorStore vectorStore;
    private final TransactionTemplate transactionTemplate;
    private final AppProperties appProperties;

    //main method to upload and convert them to chunks and store the embeddings into the vectorStore database
    public DocumentResponseDto uploadAndProcess(AuthUser user, MultipartFile file) {
        DocumentValidationService.ValidatedFile validated = validationService.validate(file);

        DocumentMetadata document = transactionTemplate.execute(status -> documentRepo.save(DocumentMetadata.builder()
                .owner(userRepository.getReferenceById(user.id()))
                .filename(validated.filename())
                .contentType(validated.contentType())
                .fileSize((long) validated.bytes().length)
                .status(DocumentStatus.PROCESSING)
                .build()));
        UUID documentId = document.getId();
        log.info("Processing document {} for user {}", documentId, user.id());

        try {
            List<Document> parsed = parserService.parse(validated);
            List<Document> chunks = parsed.isEmpty() ? List.of() : ingestionService.split(document, user.id(), parsed);
            if (chunks.isEmpty()) {
                throw new DocumentProcessingException(
                        "No extractable text was found. Scanned or image-only documents are not supported.");
            }
            ingestionService.store(document, user.id(), chunks);
            Integer pages = "application/pdf".equals(validated.contentType()) ? parsed.size() : null;
            markIndexed(user.id(), documentId, pages, chunks.size());
            return DocumentResponseDto.builder()
                    .id(documentId)
                    .filename(document.getFilename())
                    .fileSize(validated.bytes().length)
                    .status(DocumentStatus.INDEXED)
                    .chunksCreated(chunks.size())
                    .message("Document successfully processed and indexed.")
                    .build();
        } catch (ApiException e) {
            DocumentMetadataDto failed = markFailed(user.id(), documentId, e.getMessage());
            throw new DocumentProcessingException(e.getMessage(), failed, e);
        } catch (RuntimeException e) {
            log.error("Unexpected failure processing document {}", documentId, e);
            DocumentMetadataDto failed = markFailed(user.id(), documentId, "Unexpected error while processing the document.");
            throw new DocumentProcessingException("Unexpected error while processing the document.", failed, e);
        }
    }

    // Processes each file independently so one bad file does not abort the rest.
    public List<DocumentResponseDto> uploadMultipleDocuments(AuthUser user, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "No files were uploaded");
        }
        int max = appProperties.getDocuments().getMaxFilesPerRequest();
        if (files.size() > max) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOO_MANY_FILES", "At most " + max + " files can be uploaded at once");
        }
        List<DocumentResponseDto> results = new ArrayList<>();
        for (MultipartFile file : files) {
            try {
                results.add(uploadAndProcess(user, file));
            } catch (ApiException e) {
                UUID id = e.getData() instanceof DocumentMetadataDto d ? d.getId() : null;
                results.add(DocumentResponseDto.builder()
                        .id(id)
                        .filename(DocumentValidationService.sanitizeFilename(file.getOriginalFilename()))
                        .fileSize(file.getSize())
                        .status(DocumentStatus.FAILED)
                        .chunksCreated(0)
                        .message(e.getMessage())
                        .build());
            }
        }
        return results;
    }

    @Transactional(readOnly = true)
    public List<DocumentMetadataDto> getAllDocuments(AuthUser user) {
        return documentRepo.findAllByOwnerIdOrderByCreatedAtDesc(user.id()).stream()
                .map(DocumentMetadataDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentMetadataDto getDocumentById(AuthUser user, UUID id) {
        return DocumentMetadataDto.from(findOwned(user.id(), id));
    }

    // Vectors and the metadata row are deleted in one database transaction (same DataSource).
    @Transactional
    public void deleteDocumentById(AuthUser user, UUID id) {
        DocumentMetadata document = findOwned(user.id(), id);
        try {
            vectorStore.delete(VectorMetadata.ownedDocument(user.id(), id));
        } catch (RuntimeException e) {
            log.error("Could not delete vectors for document {}", id, e);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "DELETE_FAILED", "The document could not be deleted. Please try again.");
        }
        documentRepo.delete(document);
        log.info("Deleted document {} for user {}", id, user.id());
    }

    private DocumentMetadata findOwned(Long ownerId, UUID id) {
        return documentRepo.findByIdAndOwnerId(id, ownerId).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    private void markIndexed(Long ownerId, UUID documentId, Integer pages, int chunks) {
        transactionTemplate.executeWithoutResult(status -> {
            var found = documentRepo.findByIdAndOwnerId(documentId, ownerId);
            if (found.isEmpty()) {
                // deleted by the user while it was being indexed: do not leave orphaned vectors behind
                vectorStore.delete(VectorMetadata.ownedDocument(ownerId, documentId));
                return;
            }
            DocumentMetadata d = found.get();
            d.setStatus(DocumentStatus.INDEXED);
            d.setTotalPages(pages);
            d.setTotalChunks(chunks);
            d.setErrorMessage(null);
        });
    }

    private DocumentMetadataDto markFailed(Long ownerId, UUID documentId, String reason) {
        return transactionTemplate.execute(status -> documentRepo.findByIdAndOwnerId(documentId, ownerId)
                .map(d -> {
                    d.setStatus(DocumentStatus.FAILED);
                    d.setTotalChunks(0);
                    d.setErrorMessage(reason);
                    return DocumentMetadataDto.from(documentRepo.saveAndFlush(d));
                })
                .orElse(null));
    }
}
