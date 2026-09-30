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
    String estadoDisponibilidad) {}
