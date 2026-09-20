package co.tecnosport.api.presentation.catalogo.dto;

import java.util.UUID;

/**
 * Lo que pasó al contar. {@code sinCambios} distingue "conté y estaba bien" de "conté y corregí", y
 * va en la respuesta porque son dos mensajes distintos para quien acaba de guardar — igual que
 * {@code correccion} en {@code VarianteMedidaRespuesta}.
 *
 * <p>{@code dejaReservasSinRespaldo} es el aviso que no se puede callar: hay pedidos aceptados por
 * encima de lo que dice el conteo, y eso lo tiene que atender una persona.
 */
public record ExistenciaAjustadaRespuesta(
    UUID varianteId,
    String sku,
    String nombreProducto,
    int saldoAnterior,
    int saldoNuevo,
    int diferencia,
    int unidadesReservadas,
    boolean sinCambios,
    boolean dejaReservasSinRespaldo) {}
