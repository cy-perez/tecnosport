package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.EstadoProducto;
import java.util.UUID;

/**
 * Una variante activa a la que le falta el {@link co.tecnosport.api.domain.catalogo.Paquete}, con
 * lo mínimo para reconocerla en el panel y salir a medirla: de qué producto es, qué SKU tiene y si
 * ese producto ya está a la venta.
 *
 * <p>Lleva el {@code estadoProducto} porque no todas pesan igual. Una en {@code BORRADOR} es
 * trabajo a medias, que es normal; una en {@code PUBLICADO} está a la venta hoy y solo se puede
 * entregar con recogida en el punto (adr/0046), así que cada comprador de otra ciudad que la ponga
 * en el carrito se topa con un {@code 409} al cotizar.
 */
public record VarianteSinMedir(
    UUID varianteId,
    UUID productoId,
    String nombreProducto,
    String sku,
    EstadoProducto estadoProducto) {}
