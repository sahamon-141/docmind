package com.example.docmind.service;

import com.example.docmind.dto.DocumentMetadataDto;
import com.example.docmind.dto.DocumentResponseDto;
import com.example.docmind.entity.DocumentMetadata;
import com.example.docmind.entity.DocumentStatus;
import com.example.docmind.exception.DocumentProcessingException;
import com.example.docmind.exception.ResourceNotFoundException;
import com.example.docmind.repository.DocumentMetaDataRepo;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.modelmapper.ModelMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentMetaDataService {
    private static final Logger log = LoggerFactory.getLogger(DocumentMetaDataService.class);

    private final DocumentMetaDataRepo documentMetaDataRepo;
    private final JdbcTemplate jdbcTemplate;
    private final DocumentParserService parserService;
    private final DocumentIngestionService ingestionService;
    private final ModelMapper modelMapper;

    //main method to upload and convert them to chunks and store the embeddings into the vectorStore database
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

        documentMetadata = documentMetaDataRepo.save(documentMetadata);
        List<Document> parsedDocs = null;
        int chunksCreated = 0;
        try {
            //parse the file
            parsedDocs = parserService.parse(file);

            //ingest service
            chunksCreated = ingestionService.ingest(documentMetadata, parsedDocs);
        } catch (
                DocumentProcessingException e
        ) {
            //delete the intially metadata too if unable to parse and ingest
            log.info("Document metadata deleting due to fail processing");
            documentMetaDataRepo.delete(documentMetadata);
            throw e;
        }

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

    // use the previous method multiple times for each file
    public List<DocumentResponseDto> uploadMultipleDocuments(List<MultipartFile> files) {
        List<DocumentResponseDto> responseDtos = new ArrayList<>();
        for(MultipartFile file:files){
            responseDtos.add(this.uploadAndProcess(file));
        }
        return responseDtos;
    }

    // get all documents method (created a seperated Dto for this one)
    public List<DocumentMetadataDto> getAllDocuments() {

        List<DocumentMetadata> allDocuments = documentMetaDataRepo.findAllByOrderByCreatedAtDesc();
        return allDocuments.stream()
                .map(documentMetadata -> modelMapper.map(documentMetadata, DocumentMetadataDto.class))
                .toList();
    }

    // get document by id
    public DocumentMetadataDto getDocumentById(UUID id) {
        DocumentMetadata documentMetadata = documentMetaDataRepo.findById(id).orElseThrow(()->new ResourceNotFoundException("document with given id not found"));
        return modelMapper.map(documentMetadata,DocumentMetadataDto.class);
    }

    // delete document by id
    public void deleteDocumentById(UUID id) {
        DocumentMetadata documentMetadata = documentMetaDataRepo.findById(id).orElseThrow(()->new ResourceNotFoundException("document with given id not found"));
        try{
            /* this query is important
            metadata is a column in table of type JSON
            metadata->>'documentId' means that the operator will extract the documentId attribute from the JSON
            after fetching the documetId it will compare it with ?
            the question mark is the placeholder value for the id , we pass the id when using update function of the jdbcTemplate
            */
            String deleteVectorSql = "DELETE FROM vector_store WHERE metadata->>'documentId' = ?";
            int deletedCount = jdbcTemplate.update(deleteVectorSql,id.toString());
            //delete the metadata of the file too
            //documentMetaDataRepo.deleteById(id);
            log.info("Deleted {} vector chunks for document id:{}",deletedCount,id);
        }
        catch (Exception e){
            log.warn("could not delete vector store directly:{}",e.getMessage());
        }
    }
}
