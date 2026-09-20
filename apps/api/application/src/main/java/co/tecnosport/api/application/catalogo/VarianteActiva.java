package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.EstadoProducto;
import java.util.UUID;

/**
 * Una variante a la venta, con lo mínimo para reconocerla en el panel y con la existencia que
 * <b>declara el catálogo</b> — que no tiene por qué ser la del libro de inventario (adr/0049).
 *
 * <p>Gemela de {@link VarianteSinMedir}, y por el mismo motivo: el panel necesita recorrer el
 * catálogo entero por una columna concreta, y traerse los productos completos con sus imágenes y
 * sus atributos para leer un número de cada variante es caro y no aporta nada.
 *
 * <p>Solo las {@code ACTIVA}, igual que aquella: contar una variante retirada no corrige nada que
 * alguien vaya a vender, y su fila solo ensuciaría la lista de lo que sí hay que revisar.
 */
public record VarianteActiva(
    UUID varianteId,
    UUID productoId,
    String nombreProducto,
    String sku,
    EstadoProducto estadoProducto,
    int existenciaDeclarada) {}
