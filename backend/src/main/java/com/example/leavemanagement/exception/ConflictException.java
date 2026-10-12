package com.example.leavemanagement.exception;

// The action conflicts with the current state (e.g. approving a non-pending
// request, or approval would exceed the balance). Mapped to 409.
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
