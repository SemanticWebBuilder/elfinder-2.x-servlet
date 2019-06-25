package cn.bluejoe.elfinder.controller;

/**
 * 
 * @author jose.jimenez
 */
public class DataNotValidException extends Exception {

    public DataNotValidException() {
    }

    public DataNotValidException(String message) {
        super(message);
    }

    public DataNotValidException(String message, Throwable cause) {
        super(message, cause);
    }
    
}
