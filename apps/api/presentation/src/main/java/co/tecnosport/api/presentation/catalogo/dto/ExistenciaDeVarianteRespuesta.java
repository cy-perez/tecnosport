package co.tecnosport.api.presentation.catalogo.dto;

import java.util.UUID;

/**
 * Una fila de la pantalla de existencias, con las tres cifras separadas: lo que declara el catálogo
 * —y por tanto lo que ve quien compra—, lo que dice el libro de movimientos, y lo que de verdad se
 * puede vender ahora mismo.
 *
 * <p>{@code descuadrada} viaja calculada y no se deduce en el cliente: es la regla de adr/0049 y
 * tiene que decirla el servidor, no una comparación repetida en una plantilla.
 */
public record ExistenciaDeVarianteRespuesta(
    UUID varianteId,
    UUID productoId,
    String nombreProducto,
    String sku,
    String estadoProducto,
    int existenciaDeclarada,
    int saldoTotal,
    int disponible,
    int reservadas,
    boolean descuadrada) {}
