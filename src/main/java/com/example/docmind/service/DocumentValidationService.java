package com.example.docmind.service;

import com.example.docmind.config.AppProperties;
import com.example.docmind.exception.ApiException;
import com.example.docmind.exception.UnsupportedDocumentException;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Validates an upload before anything is stored: non-empty, allowed extension, and the actual bytes
 * (sniffed by Apache Tika) must match a type expected for that extension. The browser-supplied
 * Content-Type is ignored because clients can set it to anything.
 */
@Service
@RequiredArgsConstructor
public class DocumentValidationService {
    private static final Tika TIKA = new Tika();

    private static final Map<String, Set<String>> EXPECTED_TYPES = Map.ofEntries(
            Map.entry("pdf", Set.of("application/pdf")),
            Map.entry("txt", Set.of("text/plain")),
            Map.entry("md", Set.of("text/plain", "text/markdown", "text/x-web-markdown")),
            Map.entry("markdown", Set.of("text/plain", "text/markdown", "text/x-web-markdown")),
            Map.entry("csv", Set.of("text/csv", "text/plain")),
            Map.entry("html", Set.of("text/html", "application/xhtml+xml")),
            Map.entry("htm", Set.of("text/html", "application/xhtml+xml")),
            Map.entry("rtf", Set.of("application/rtf", "text/rtf")),
            Map.entry("doc", Set.of("application/msword", "application/x-tika-msoffice")),
            Map.entry("docx", union("application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
            Map.entry("pptx", union("application/vnd.openxmlformats-officedocument.presentationml.presentation")),
            Map.entry("odt", Set.of("application/vnd.oasis.opendocument.text", "application/zip")));

    private final AppProperties appProperties;

    public record ValidatedFile(String filename, String extension, String contentType, byte[] bytes) {
    }

    public ValidatedFile validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "The uploaded file is empty");
        }
        String filename = sanitizeFilename(file.getOriginalFilename());
        String extension = extensionOf(filename);
        if (extension.isEmpty() || !appProperties.getDocuments().allowedExtensionList().contains(extension)
                || !EXPECTED_TYPES.containsKey(extension)) {
            throw new UnsupportedDocumentException("Unsupported file type. Supported: "
                    + String.join(", ", appProperties.getDocuments().allowedExtensionList()));
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNREADABLE_FILE", "Could not read the uploaded file");
        }

        String detected = TIKA.detect(bytes, filename).toLowerCase(Locale.ROOT);
        if (!EXPECTED_TYPES.get(extension).contains(detected)) {
            throw new UnsupportedDocumentException("File content does not match its ." + extension + " extension");
        }
        return new ValidatedFile(filename, extension, detected, bytes);
    }

    static String sanitizeFilename(String original) {
        String name = original == null ? "" : original;
        // drop any client-side path, then control characters
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isEmpty()) name = "document";
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 || dot == filename.length() - 1 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    // Tika may only recognise the generic OOXML/zip container when the specific detector is unavailable
    private static Set<String> union(String specificType) {
        return Set.of(specificType, "application/x-tika-ooxml", "application/zip");
    }
}
