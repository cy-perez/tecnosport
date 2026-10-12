package co.tecnosport.api.presentation.catalogo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;

/** Los precios como tienen que quedar; las variantes que no vengan no se tocan. */
public record CambiarPreciosPeticion(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PrecioDeVariantePeticion> precios) {

  public CambiarPreciosPeticion {
    Objects.requireNonNull(precios, "Faltan los precios.");
    precios = List.copyOf(precios);
  }
}
