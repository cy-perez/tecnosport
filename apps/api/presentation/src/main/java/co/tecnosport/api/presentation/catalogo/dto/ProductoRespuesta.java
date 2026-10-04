package co.tecnosport.api.presentation.catalogo.dto;

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
     * Si las fotos sin variante acompañan a las de cada color en la galería. Falso en el producto
     * que solo trae fotos por color: ahí, elegido un color, se muestran solo las suyas.
     */
    boolean fotosGeneralesEnCadaColor) {}
