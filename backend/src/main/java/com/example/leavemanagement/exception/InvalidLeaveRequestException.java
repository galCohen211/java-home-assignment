package com.example.leavemanagement.exception;

// A new leave request breaks a business rule (e.g. not enough balance). Mapped to 400.
public class InvalidLeaveRequestException extends RuntimeException {
    public InvalidLeaveRequestException(String message) {
        super(message);
    }
}
