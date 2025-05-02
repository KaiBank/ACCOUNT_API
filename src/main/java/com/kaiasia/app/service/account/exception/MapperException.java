package com.kaiasia.app.service.account.exception;

public class MapperException extends CustomException{
    public MapperException(Throwable cause) {
        super(cause);
    }

    public MapperException(String message, Throwable cause) {
        super(message, cause);
    }
}
