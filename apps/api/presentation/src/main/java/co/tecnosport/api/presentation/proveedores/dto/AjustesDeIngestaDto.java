package co.tecnosport.api.presentation.proveedores.dto;

import co.tecnosport.api.domain.proveedores.AjusteDeTalla;
import co.tecnosport.api.domain.proveedores.AjustesDeIngesta;
import co.tecnosport.api.domain.proveedores.TallasPorGenero;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;

/**
 * Cómo se ingiere el chat del proveedor (ADR-0089), de ida y de vuelta. Los dos interruptores son
 * envoltorios porque un {@code boolean} ausente revienta la deserialización (apps/api/CLAUDE.md):
 * nulo es que no.
 *
 * @param diasDeVigencia nulo es la ventana general
 * @param tallasPorGenero nulo si las tallas las dice cada anuncio
 */
public record AjustesDeIngestaDto(
    Boolean unaFotoUnProducto,
    Boolean ventaBajoConfirmacion,
    Integer diasDeVigencia,
    TallasPorGeneroDto tallasPorGenero) {

  /**
   * @param dama como las nombra el proveedor; vacía si no maneja de dama
   * @param caballero como las nombra el proveedor; vacía si no maneja de caballero
   * @param ajuste cuántas tallas más arriba las nombra: 1 si son EUR
   */
  public record TallasPorGeneroDto(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> dama,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> caballero,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer ajuste) {

    public TallasPorGeneroDto {
      dama = dama == null ? List.of() : List.copyOf(dama);
      caballero = caballero == null ? List.of() : List.copyOf(caballero);
      Objects.requireNonNull(ajuste, "El ajuste de talla es obligatorio con las tallas.");
    }
  }

  public AjustesDeIngesta aDominio() {
    return new AjustesDeIngesta(
        Boolean.TRUE.equals(unaFotoUnProducto),
        Boolean.TRUE.equals(ventaBajoConfirmacion),
        diasDeVigencia,
        tallasPorGenero == null
            ? null
            : new TallasPorGenero(
                tallasPorGenero.dama(),
                tallasPorGenero.caballero(),
                new AjusteDeTalla(tallasPorGenero.ajuste())));
  }

  public static AjustesDeIngestaDto de(AjustesDeIngesta ajustes) {
    return new AjustesDeIngestaDto(
        ajustes.unaFotoUnProducto(),
        ajustes.ventaBajoConfirmacion(),
        ajustes.diasDeVigencia(),
        ajustes
            .tallasPorGeneroOpcional()
            .map(t -> new TallasPorGeneroDto(t.dama(), t.caballero(), t.ajuste().tallas()))
            .orElse(null));
  }
}
