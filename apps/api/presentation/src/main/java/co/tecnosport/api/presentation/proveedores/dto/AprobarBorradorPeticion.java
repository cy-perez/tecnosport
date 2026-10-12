package co.tecnosport.api.presentation.proveedores.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record AprobarBorradorPeticion(
    String titulo,
    String descripcion,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID categoriaId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID marcaId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long precioVenta,
    TallasPeticion tallas,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int existenciaInicial,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String altEs,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String altEn,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<FotoAprobadaPeticion> fotos,
    List<PrendaAprobadaPeticion> prendas) {

  public AprobarBorradorPeticion {
    Objects.requireNonNull(categoriaId, "La categoría es obligatoria.");
    Objects.requireNonNull(marcaId, "La marca es obligatoria.");
    Objects.requireNonNull(altEs, "El texto alternativo en español es obligatorio.");
    Objects.requireNonNull(altEn, "El texto alternativo en inglés es obligatorio.");
    Objects.requireNonNull(fotos, "Hay que decir qué fotos se publican.");
  }

  /**
   * @param tono el color que muestra la foto; nulo si es del producto entero
   * @param prenda a qué prenda pertenece, desde 1: las fotos con el mismo número son una sola
   *     variante y llevan el mismo tono. Nulo = una foto con tono es una prenda ella sola
   */
  public record FotoAprobadaPeticion(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID mensajeId,
      String tono,
      String colorHex,
      Integer prenda) {
    public FotoAprobadaPeticion {
      Objects.requireNonNull(mensajeId, "La foto se nombra por su mensaje.");
    }
  }

  /**
   * Una prenda del producto, con foto propia o sin ella: la que no cita ninguna foto se ve en la
   * que retrata todos los tonos.
   *
   * @param numero el que citan las fotos en {@code prenda}, desde 1
   * @param tono el color, sin numerar
   * @param tallas en cuáles tallas del producto viene; nula = en todas
   */
  public record PrendaAprobadaPeticion(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int numero,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String tono,
      String colorHex,
      List<String> tallas) {
    public PrendaAprobadaPeticion {
      Objects.requireNonNull(tono, "Una prenda tiene color.");
    }
  }
}
