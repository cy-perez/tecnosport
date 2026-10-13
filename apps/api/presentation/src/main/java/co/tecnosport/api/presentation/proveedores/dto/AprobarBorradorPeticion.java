package co.tecnosport.api.presentation.proveedores.dto;

import co.tecnosport.api.application.proveedores.AprobarBorradorComando;
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
    List<PrendaAprobadaPeticion> prendas,
    /**
     * Si la principal que vale para todos los tonos entra también en la galería de la ficha. Nulo =
     * no, como antes de existir el campo.
     */
    Boolean principalEnLaFicha) {

  public AprobarBorradorPeticion {
    Objects.requireNonNull(categoriaId, "La categoría es obligatoria.");
    Objects.requireNonNull(marcaId, "La marca es obligatoria.");
    Objects.requireNonNull(altEs, "El texto alternativo en español es obligatorio.");
    Objects.requireNonNull(altEn, "El texto alternativo en inglés es obligatorio.");
    Objects.requireNonNull(fotos, "Hay que decir qué fotos se publican.");
  }

  /** El comando de la aprobación de ese borrador, con todo lo que trae el cuerpo. */
  public AprobarBorradorComando aComando(UUID borradorId) {
    return new AprobarBorradorComando(
        borradorId,
        titulo,
        descripcion,
        categoriaId,
        marcaId,
        precioVenta,
        tallas == null ? null : tallas.aDominio(),
        existenciaInicial,
        altEs,
        altEn,
        fotos.stream()
            .map(
                f ->
                    new AprobarBorradorComando.FotoAprobada(
                        f.mensajeId(), f.tono(), f.colorHex(), f.prenda()))
            .toList(),
        prendas == null
            ? List.of()
            : prendas.stream()
                .map(
                    p ->
                        new AprobarBorradorComando.PrendaAprobada(
                            p.numero(),
                            p.tono(),
                            p.colorHex(),
                            p.tallas(),
                            p.seVeEn(),
                            p.referencia()))
                .toList(),
        Boolean.TRUE.equals(principalEnLaFicha));
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
   * @param seVeEn la foto que la retrata junto con otros tonos, si no tiene foto propia; nula = la
   *     de todos
   * @param referencia el SKU con que el proveedor vende la prenda, el del pie de su foto en un
   *     álbum: queda en sus variantes para poder agotarla sola. Nula si no tiene
   */
  public record PrendaAprobadaPeticion(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int numero,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String tono,
      String colorHex,
      List<String> tallas,
      UUID seVeEn,
      String referencia) {
    public PrendaAprobadaPeticion {
      Objects.requireNonNull(tono, "Una prenda tiene color.");
    }
  }
}
