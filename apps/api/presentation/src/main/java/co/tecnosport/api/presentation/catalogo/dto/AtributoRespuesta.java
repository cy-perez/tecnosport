package co.tecnosport.api.presentation.catalogo.dto;

import java.util.List;
import java.util.UUID;

public record AtributoRespuesta(
    UUID id, String nombre, String tipo, List<String> valoresPermitidos) {}
