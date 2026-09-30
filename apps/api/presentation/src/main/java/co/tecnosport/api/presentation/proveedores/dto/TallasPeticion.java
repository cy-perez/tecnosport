package co.tecnosport.api.presentation.proveedores.dto;

import co.tecnosport.api.domain.proveedores.Tallas;
import co.tecnosport.api.domain.proveedores.TipoDeTalla;
import java.util.List;

/**
 * @param tipo UNICA, LISTA o DESCONOCIDA
 */
public record TallasPeticion(String tipo, String sirveHasta, List<String> valores) {

  public Tallas aDominio() {
    return new Tallas(
        tipo == null ? TipoDeTalla.DESCONOCIDA : TipoDeTalla.valueOf(tipo), sirveHasta, valores);
  }
}
