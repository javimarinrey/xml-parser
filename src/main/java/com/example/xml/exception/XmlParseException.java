package com.example.xml.exception;

/**
 * Thrown when the XML is malformed or missing required tags.
 */
public class XmlParseException extends RuntimeException {

    public XmlParseException(String message) {
        super(message);
    }

    public XmlParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
