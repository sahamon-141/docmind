package com.example.docmind.exception;

public class ResourceNotFoundException extends RuntimeException {
    ResourceNotFoundException(){
        super("Resources you are looking for not found");
    }
    ResourceNotFoundException(String message){
        super(message);
    }
    ResourceNotFoundException(String message,Throwable ex){
        super(message,ex);
    }
}
