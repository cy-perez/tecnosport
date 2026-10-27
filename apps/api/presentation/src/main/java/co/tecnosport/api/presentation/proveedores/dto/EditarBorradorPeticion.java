package co.tecnosport.api.presentation.proveedores.dto;

import java.util.List;

/**
 * Todo opcional: lo que no venga se queda como estaba. El precio es un entero de pesos y es el
 * <b>de contado</b> sugerido (ADR-0095); el publicado lo calcula el servidor.
 */
public record EditarBorradorPeticion(
    String titulo,
    String tipo,
    Long precioVentaSugerido,
    TallasPeticion tallas,
    Integer cantidadTonos,
    List<String> tonosNombrados,
    String material,
    String descripcion,
    String altEn,
    /** Cuántas tallas más arriba las nombra el proveedor, de -4 a 4 (ADR-0085). Nulo = igual. */
    Integer ajusteDeTalla) {}
