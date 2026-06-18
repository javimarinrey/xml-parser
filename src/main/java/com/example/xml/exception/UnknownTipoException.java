package com.example.xml.exception;

/**
 * Thrown when the &lt;tipo&gt; value has no registered parser.
 */
public class UnknownTipoException extends RuntimeException {

    private final String tipo;

    public UnknownTipoException(String tipo) {
        super("No hay parser registrado para el tipo: " + tipo);
        this.tipo = tipo;
    }

    public String getTipo() {
        return tipo;
    }
}
