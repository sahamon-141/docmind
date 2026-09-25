package com.example.docmind.service;

import com.example.docmind.config.AppProperties;
import com.example.docmind.entity.DocumentMetadata;
import com.example.docmind.entity.DocumentStatus;
import com.example.docmind.exception.DocumentProcessingException;
import com.example.docmind.repository.DocumentMetaDataRepo;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DocumentIngestionService {
    private final VectorStore vectorStore;
    private final DocumentMetaDataRepo metaDataRepo;
    private final AppProperties appProperties;

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    public int ingest(DocumentMetadata metadata, List<Document> parsedDocs) {
        log.info("Ingesting document [id={}, name={}, size={}]",metadata.getId(),metadata.getFilename(),metadata.getFileSize());
        try{
           metadata.setStatus(DocumentStatus.PROCESSING);
           metadata.setTotalPages(parsedDocs.size());

           // Text Chunking , using tokenTextSplitter
            TokenTextSplitter tokenTextSplitter = TokenTextSplitter.builder()
                    .withChunkSize(appProperties.getRag().getChunkSize()) // defines how much size should each token
                    .withMinChunkSizeChars(appProperties.getRag().getMinChunkSizeChars()) // Dont stop at a punctuation mark if we've only accumulated only a small amount of text
                    .withMinChunkLengthToEmbed(appProperties.getRag().getMinChunkLengthToEmbed()) // Minimum number of characters required before chunk is kept
                    .withMaxNumChunks(appProperties.getRag().getMaxNumChunks()) //safety feature , maximum number of chunks that can be genrated from a single document
                    .withKeepSeparator(true)
                    .build();
            List<Document> chunks = tokenTextSplitter.apply(parsedDocs);
            if(chunks.isEmpty()){
                metadata.setStatus(DocumentStatus.FAILED);
                metadata.setErrorMessage("Documents appears to be empty or unreadable");
                metaDataRepo.save(metadata);
                return 0;
            }

            // Metadata enrichment on each chunk
            List<Document> enrichedChunks = new ArrayList<>();
            for(int i=0;i<chunks.size();i++){
                Document  chunk = chunks.get(i);
                Map<String,Object> enrichedMetadata = new HashMap<>(chunk.getMetadata());
                enrichedMetadata.put("documentId",metadata.getId());
                enrichedMetadata.put("fileName",metadata.getFilename());
                enrichedMetadata.put("contentType",metadata.getContentType());
                enrichedMetadata.put("chunkIndex",i);
                Object pageNumber = chunk.getMetadata().get("page_number");
                if(pageNumber==null){
                    pageNumber = chunk.getMetadata().get("pageNumber");
                }
                if(pageNumber!=null){
                    enrichedMetadata.put("pageNumber",pageNumber);
                }
                Document enrichedDoc = new Document(chunk.getText(),enrichedMetadata);
                enrichedChunks.add(enrichedDoc);
            }
            // write chunks and embedding to pgvector
            log.info("Writing {} vector chunks to PgVectorStore for document: {}",enrichedChunks.size(),metadata.getFilename());
            vectorStore.add(enrichedChunks);

            // Update document status to index
            metadata.setStatus(DocumentStatus.INDEXED);
            metadata.setTotalChunks(enrichedChunks.size());
            metadata.setErrorMessage(null);
            metaDataRepo.save(metadata);
            log.info("Succesfully indexed document [id={},name={}, chunks={}]",metadata.getId(),metadata.getFilename(),enrichedChunks.size());
            return enrichedChunks.size();
        }
        catch (Exception ex){
            log.error("Failed to ingest document into vector store: {}",metadata.getFilename());
            metadata.setStatus(DocumentStatus.FAILED);
            metadata.setErrorMessage(ex.getMessage());
            metaDataRepo.save(metadata);
            throw new DocumentProcessingException("Failed to index Document"+ ex.getMessage());
        }
    }
}
