package com.example.xml.model;

/**
 * DTO for tipo = MODIFICACION.
 *
 * Example XML:
 * <pre>{@code
 * <solicitud>
 *   <tipo>MODIFICACION</tipo>
 *   <id>12345</id>
 *   <campo>email</campo>
 *   <valor>nuevo@example.com</valor>
 * </solicitud>
 * }</pre>
 */
public record ModificacionRequest(String id, String campo, String valor) {}
