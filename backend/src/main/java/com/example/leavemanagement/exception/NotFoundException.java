package com.example.leavemanagement.exception;

// The referenced employee or leave request does not exist. Mapped to 404.
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
