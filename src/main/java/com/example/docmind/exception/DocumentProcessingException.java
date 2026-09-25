package com.example.docmind.exception;

public class DocumentProcessingException extends RuntimeException{
    public DocumentProcessingException(String message){
        super(message);
    }
    DocumentProcessingException(){
        super("Error in Document Processing");
    }
    public DocumentProcessingException(String message, Throwable ex){
        super(message,ex);
    }
}
