/*
 * © 2016 Toru Takahashi
 */
package com.torutk.tinymap;

/**
 *
 * @author Toru Takahashi
 */
public class TinyMapException extends Exception {

    /**
     * Creates a new instance of <code>TinyMapException</code> without detail
     * message.
     */
    public TinyMapException() {
    }

    /**
     * Constructs an instance of <code>TinyMapException</code> with the
     * specified detail message.
     *
     * @param msg the detail message.
     */
    public TinyMapException(String msg) {
        super(msg);
    }

    public TinyMapException(String message, Throwable cause) {
        super(message, cause);
    }

    public TinyMapException(Throwable cause) {
        super(cause);
    }
    
    
}
