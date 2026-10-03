package co.tecnosport.api.presentation.proveedores.dto;

import java.util.List;

/** Todo opcional: lo que no venga se queda como estaba. El precio es un entero de pesos. */
public record EditarBorradorPeticion(
    String titulo,
    String tipo,
    Long precioVentaSugerido,
    TallasPeticion tallas,
    Integer cantidadTonos,
    List<String> tonosNombrados,
    String material,
    String descripcion,
    String altEn) {}
