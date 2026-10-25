package co.tecnosport.api.presentation.catalogo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ProductoRespuesta(
    String slug,
    String nombre,
    String descripcion,
    MarcaRespuesta marca,
    CategoriaRespuesta categoria,
    ImagenRespuesta imagenPrincipal,
    List<ImagenRespuesta> galeria,
    RotacionRespuesta rotacion,
    List<VarianteRespuesta> variantes,
    /**
     * DISPONIBLE, OCULTO_POR_VENCIMIENTO o AGOTADO_POR_PROVEEDOR. Un producto publicado que el
     * proveedor ya no tiene sigue respondiendo su ficha —los enlaces no mueren— pero no se lista ni
     * se compra; el cliente lo pinta como no disponible.
     */
    String estadoDisponibilidad,
    /**
     * Las tallas de la categoría en su orden, heredadas de la rama si la hoja no tiene las suyas.
     * Solo en la ficha; en la rejilla viene vacía, porque la tarjeta no la usa.
     */
    List<String> escalaTallas,
    /** Hasta qué talla le sirve una prenda de talla única, si el proveedor lo dijo. */
    String tallaSirveHasta,
    /**
     * Los colores en que el fabricante publica el modelo (ADR-0086): la ficha de tecnología los
     * dice al pie de las fotos. No son los que hay; vacía si no se conocen.
     */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> paleta) {}
