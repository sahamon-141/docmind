package com.example.docmind.service;

import com.example.docmind.exception.DocumentProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

// PDFs are read page by page (so citations carry page numbers); everything else goes through Apache Tika.
@Service
public class DocumentParserService {
    private static final Logger log = LoggerFactory.getLogger(DocumentParserService.class);

    public List<Document> parse(DocumentValidationService.ValidatedFile file) {
        log.info("Parsing document: {}, size: {} bytes, type: {}", file.filename(), file.bytes().length, file.contentType());
        Resource resource = new ByteArrayResource(file.bytes()) {
            @Override //we are overriding the getFileName method
            public String getFilename() {
                return file.filename();
            }
        };
        try {
            List<Document> documents = "application/pdf".equals(file.contentType())
                    ? parsePdf(resource)
                    : new TikaDocumentReader(resource).read();
            // drop pages/sections without any extractable text
            return documents.stream().filter(d -> d.getText() != null && !d.getText().isBlank()).toList();
        } catch (RuntimeException e) {
            log.warn("Failed to parse {}: {}", file.filename(), e.getClass().getSimpleName());
            throw new DocumentProcessingException("The document could not be parsed. It may be corrupted or password-protected.");
        }
    }

    private List<Document> parsePdf(Resource resource) {
        PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                .withPageBottomMargin(0)
                .withPageTopMargin(0)
                .build();
        return new PagePdfDocumentReader(resource, config).read();
    }
}
