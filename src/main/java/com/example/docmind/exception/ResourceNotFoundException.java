package com.example.docmind.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(){
        super("Resources you are looking for not found");
    }
    public ResourceNotFoundException(String message){
        super(message);
    }
    public ResourceNotFoundException(String message,Throwable ex){
        super(message,ex);
    }
}
