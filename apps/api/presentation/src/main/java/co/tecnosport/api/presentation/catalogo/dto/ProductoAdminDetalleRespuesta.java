package co.tecnosport.api.presentation.catalogo.dto;

import java.util.List;
import java.util.UUID;

/**
 * Lo mismo que {@link ProductoAdminRespuesta} más la galería.
 *
 * <p>Separado a propósito: la lista del panel trae veinte productos por página y es deliberadamente
 * liviana —lo dice {@code MapeadorRespuestasProductoAdmin} desde la Fase 4—. Meter ahí hasta ocho
 * imágenes por fila engordaría cada página para que la pantalla de la lista no use ninguna.
 */
public record ProductoAdminDetalleRespuesta(
    UUID id,
    String nombre,
    String descripcion,
    String slug,
    String estado,
    MarcaRespuesta marca,
    CategoriaRespuesta categoria,
    String imagenPrincipalUrl,
    int totalVariantes,
    List<ImagenDeGaleriaRespuesta> galeria) {}
