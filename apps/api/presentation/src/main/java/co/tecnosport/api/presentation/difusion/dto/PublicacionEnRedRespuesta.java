package co.tecnosport.api.presentation.difusion.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Una constancia de difusión, tal como la ve el panel.
 *
 * <p>Lleva el pie entero y no un resumen: es lo que de verdad se publicó ese día, con el precio y
 * los hashtags de entonces, y es lo único que queda si alguien quiere saber qué decía un post.
 */
public record PublicacionEnRedRespuesta(
    UUID id,
    String red,
    String estado,
    String idPublicacionExterna,
    String pieDeFoto,
    String urlImagen,
    Instant solicitadaEn,
    Instant publicadaEn,
    String detalleDelFallo) {}
