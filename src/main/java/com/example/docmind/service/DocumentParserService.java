package com.example.docmind.service;

import com.example.docmind.exception.DocumentProcessingException;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
public class DocumentParserService {
    private final static Logger log = LoggerFactory.getLogger(DocumentParserService.class);
    public List<Document> parse(MultipartFile file){
        String fileName = file.getOriginalFilename()!=null?file.getOriginalFilename():"document";
        String contentType = file.getContentType()!=null?file.getContentType().toLowerCase():"";
        log.info("Parsing Document: {},size: {} bytes,contentType:{}",fileName,file.getSize(),contentType);
        try{
            Resource resource = new ByteArrayResource(file.getBytes()){
                @Override //we are overriding the getFileName method
                public @Nullable String getFilename(){
                    return fileName;
                }
            };
            //check if it is a pdf
            if(fileName.toLowerCase().endsWith(".pdf")||contentType.contains("pdf")){
                return parsePdf(resource);
            }
            //else pass it to the genericFile parser
            else{
                return parseGenericFile(resource);
            }

        }catch(IOException e){
            log.error("Failed to read file bytes {}",fileName);
            throw new DocumentProcessingException("Could not read uploaded file {}");
        }
    }

    private List<Document> parsePdf(Resource resource) {
        PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                .withPageBottomMargin(0)
                .withPageTopMargin(0)
                .build();
        PagePdfDocumentReader documentReader = new PagePdfDocumentReader(resource,config);
        return documentReader.read();
    }

    private List<Document> parseGenericFile(Resource resource) {
        TikaDocumentReader tikaDocumentReader = new TikaDocumentReader(resource);

        return tikaDocumentReader.read();
    }
}
