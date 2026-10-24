package co.tecnosport.api.presentation.catalogo.dto;

import java.time.Instant;
import java.util.UUID;

public record ProductoAdminRespuesta(
    UUID id,
    String nombre,
    String descripcion,
    String slug,
    String estado,
    MarcaRespuesta marca,
    CategoriaRespuesta categoria,
    String imagenPrincipalUrl,
    int totalVariantes,
    /**
     * Si el proveedor todavía lo tiene: {@code DISPONIBLE}, {@code OCULTO_POR_VENCIMIENTO} o {@code
     * AGOTADO_POR_PROVEEDOR}. La vitrina exige {@code PUBLICADO} <b>y</b> {@code DISPONIBLE}; sin
     * esto el panel decía "publicado" de un producto que la tienda no mostraba. Un manual siempre
     * es {@code DISPONIBLE}.
     */
    String estadoDisponibilidad,
    /** La fecha del último mensaje o lista del proveedor que lo trajo; nula en un manual. */
    Instant vistoPorUltimaVez) {}
