package com.example.docmind.service;

import com.example.docmind.dto.DocumentResponseDto;
import com.example.docmind.entity.DocumentMetadata;
import com.example.docmind.entity.DocumentStatus;
import com.example.docmind.repository.DocumentMetaDataRepo;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentMetaDataService {
    private static final Logger log = LoggerFactory.getLogger(DocumentMetaDataService.class);

    private final DocumentMetaDataRepo documentMetaDataRepo;
    private final JdbcTemplate jdbcTemplate;
    private final DocumentParserService parserService;
    private final DocumentIngestionService ingestionService;

    public DocumentResponseDto uploadAndProcess(MultipartFile file){
        String filename = file.getOriginalFilename()!=null?file.getOriginalFilename():"document";
        String contentType = file.getContentType()!=null?file.getContentType():"application/octat-stream";

        //create document metadata
        DocumentMetadata documentMetadata = DocumentMetadata.builder()
                .filename(filename)
                .contentType(contentType)
                .status(DocumentStatus.PROCESSING)
                .fileSize(file.getSize())
                .createdAt(LocalDateTime.now())
                .build();

        DocumentMetadata savedDocumentMetadata = documentMetaDataRepo.save(documentMetadata);

        //parse the file
        List<Document>  parsedDocs = parserService.parse(file);

        //ingest service
        int chunksCreated = ingestionService.ingest(documentMetadata,parsedDocs);
        documentMetadata.setTotalChunks(chunksCreated);

        //save the document metadata


        return DocumentResponseDto.builder()
                .id(documentMetadata.getId())
                .filename(documentMetadata.getFilename())
                .fileSize(documentMetadata.getFileSize())
                .documentStatus(documentMetadata.getStatus())
                .chunksCreated(chunksCreated)
                .message("Document successfully processed and indexed.")
                .build();
    }
}
