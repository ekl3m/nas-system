package com.nas_backend.exception;

import java.io.IOException;

// Custom exception for file validation errors
public class FileValidationException extends IOException {

    public FileValidationException(String message) {
        super(message);
    }
}
