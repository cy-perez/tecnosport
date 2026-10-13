package co.tecnosport.api.domain.proveedores;

import java.util.Optional;

/**
 * Qué talla pedirle al proveedor por una variante que la nombra distinto de como se vende
 * (ADR-0085): la suya, o que no se sabe —una talla agregada después de aprobar que el ajuste no
 * alcanza a contar—. «No se sabe» se dice, no se calla: callado, quien despacha pediría la que se
 * vende.
 */
public record TallaAlProveedor(String talla) {

  public TallaAlProveedor {
    talla = talla == null || talla.isBlank() ? null : talla.strip();
  }

  public static TallaAlProveedor desconocida() {
    return new TallaAlProveedor(null);
  }

  public Optional<String> conocida() {
    return Optional.ofNullable(talla);
  }
}
