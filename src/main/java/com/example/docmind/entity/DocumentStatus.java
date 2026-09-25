package com.example.docmind.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

public enum DocumentStatus {
    UPLOADING,
    PROCESSING,
    INDEXED,
    FAILED
    //TODO [Reverse Engineering] generate columns from DB
}