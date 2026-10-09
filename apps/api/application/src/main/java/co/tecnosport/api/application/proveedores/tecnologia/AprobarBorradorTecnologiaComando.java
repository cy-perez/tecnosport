package co.tecnosport.api.application.proveedores.tecnologia;

import java.util.Objects;
import java.util.UUID;

/**
 * @param marcaId y {@code categoriaId}: los del catálogo, que elige quien aprueba con las que
 *     sugiere la skill a la vista. Solo cuentan para un modelo nuevo; un producto que ya existe
 *     conserva las suyas
 */
public record AprobarBorradorTecnologiaComando(UUID borradorId, UUID marcaId, UUID categoriaId) {
  public AprobarBorradorTecnologiaComando {
    Objects.requireNonNull(borradorId, "El comando dice qué borrador se aprueba.");
  }
}
