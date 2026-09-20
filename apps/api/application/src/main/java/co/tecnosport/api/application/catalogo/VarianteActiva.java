package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.EstadoProducto;
import java.util.UUID;

/**
 * Una variante a la venta, con lo mínimo para reconocerla en el panel.
 *
 * <p>Ya no trae ninguna existencia: desde adr/0050 el catálogo no guarda ninguna. Quien la sabe es
 * el libro de movimientos, y {@code ListarExistencias} es quien cruza las dos lecturas.
 *
 * <p>Gemela de {@link VarianteSinMedir}, y por el mismo motivo: el panel necesita recorrer el
 * catálogo entero para listar sus variantes, y traerse los productos completos con sus imágenes y
 * sus atributos para leer dos campos de cada una es caro y no aporta nada.
 *
 * <p>Solo las {@code ACTIVA}, igual que aquella: contar una variante retirada no corrige nada que
 * alguien vaya a vender, y su fila solo ensuciaría la lista de lo que sí hay que revisar.
 */
public record VarianteActiva(
    UUID varianteId,
    UUID productoId,
    String nombreProducto,
    String sku,
    EstadoProducto estadoProducto) {}
