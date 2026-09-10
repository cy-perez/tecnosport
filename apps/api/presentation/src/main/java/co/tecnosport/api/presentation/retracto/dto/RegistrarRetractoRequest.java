package co.tecnosport.api.presentation.retracto.dto;

/**
 * {@code motivo} opcional: el retracto se ejerce sin justificar (Ley 1480 de 2011, art. 47).
 *
 * <p>{@code medioPreferido} opcional por otra razon: la Ley 2439 de 2024 obliga a devolver el
 * dinero por el medio que el comprador prefiera, y el comprador puede no haberlo dicho al
 * retractarse. Se anota cuando llegue, en el paso del reintegro.
 */
public record RegistrarRetractoRequest(String motivo, String medioPreferido) {}
