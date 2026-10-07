package co.tecnosport.api.presentation.difusion.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Una constancia de difusión, tal como la ve el panel.
 *
 * <p>Lleva el pie entero y no un resumen: es lo que de verdad se publicó ese día, con el precio y
 * los hashtags de entonces, y es lo único que queda si alguien quiere saber qué decía un post.
 *
 * <p>{@code urlImagen} es la primera del carrusel —la que el panel pinta como miniatura— y {@code
 * urlesImagen} son todas. Se mandan las dos y no solo la lista porque el panel enseña una miniatura
 * por difusión y no un carrusel, y hacerle sacar el primer elemento sería darle una regla que ya
 * está tomada en el dominio.
 */
public record PublicacionEnRedRespuesta(
    UUID id,
    String red,
    String estado,
    String idPublicacionExterna,
    String pieDeFoto,
    String urlImagen,
    List<String> urlesImagen,
    Instant solicitadaEn,
    Instant publicadaEn,
    String detalleDelFallo) {}
