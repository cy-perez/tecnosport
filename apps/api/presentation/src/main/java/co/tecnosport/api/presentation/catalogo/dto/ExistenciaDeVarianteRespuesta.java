package co.tecnosport.api.presentation.catalogo.dto;

import java.util.UUID;

/**
 * Una fila de la pantalla de existencias: lo que dice el libro de movimientos, lo que retienen las
 * reservas en vuelo y lo que de verdad se puede vender ahora mismo.
 *
 * <p>Tenía una cifra más, {@code existenciaDeclarada}, y con ella un {@code descuadrada} que
 * comparaba las dos. Las dos desaparecieron con la columna del catálogo en adr/0050: el libro es
 * ahora la única existencia que hay.
 */
public record ExistenciaDeVarianteRespuesta(
    UUID varianteId,
    UUID productoId,
    String nombreProducto,
    String sku,
    String estadoProducto,
    int saldoTotal,
    int disponible,
    int reservadas) {}
