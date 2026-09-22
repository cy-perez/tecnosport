package co.tecnosport.api.presentation.catalogo.dto;

import java.util.List;
import java.util.UUID;

/**
 * Lo mismo que {@link ProductoAdminRespuesta} más la galería, y con la imagen principal **entera**
 * en vez de su sola URL.
 *
 * <p>Separado a propósito: la lista del panel trae veinte productos por página y es deliberadamente
 * liviana —lo dice {@code MapeadorRespuestasProductoAdmin} desde la Fase 4—. Meter ahí hasta ocho
 * imágenes por fila engordaría cada página para que la pantalla de la lista no use ninguna. Por eso
 * la lista se queda con {@code imagenPrincipalUrl}, que es todo lo que su pantalla pinta.
 *
 * <p><b>El detalle, en cambio, devuelve la imagen principal con sus variantes.</b> Con una sola URL
 * no había forma de saber desde fuera qué anchos existen de ella, y eso dejaba ciego al informe de
 * huérfanos: daba por no reclamados los anchos pequeños y el JPEG de vista previa de cada
 * principal, estando vivos. Ver `ADR-0057` y la deuda 24 de docs/09.
 */
public record ProductoAdminDetalleRespuesta(
    UUID id,
    String nombre,
    String descripcion,
    String slug,
    String estado,
    MarcaRespuesta marca,
    CategoriaRespuesta categoria,
    ImagenRespuesta imagenPrincipal,
    int totalVariantes,
    List<ImagenDeGaleriaRespuesta> galeria) {}
