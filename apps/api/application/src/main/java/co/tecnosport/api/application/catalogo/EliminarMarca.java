package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Marca;
import java.util.Objects;
import java.util.UUID;

/**
 * Borra una marca <b>sin productos</b>: la que se creó por error o la que dejó de venderse y ya no
 * tiene nada detrás. Con productos, en cualquier estado, se rechaza diciendo cuántos; reasignarlos
 * en bloque a otra marca no existe a propósito —cambiaría fichas publicadas sin que nadie las
 * mire—. Ver {@code ADR-0076}.
 */
public final class EliminarMarca {

  private final RepositorioMarcas repositorioMarcas;

  public EliminarMarca(RepositorioMarcas repositorioMarcas) {
    this.repositorioMarcas =
        Objects.requireNonNull(repositorioMarcas, "El repositorio de marcas no puede ser nulo.");
  }

  public void ejecutar(UUID id) {
    Objects.requireNonNull(id, "El id no puede ser nulo.");
    Marca marca =
        repositorioMarcas.buscarPorId(id).orElseThrow(() -> new MarcaNoEncontradaException(id));
    long productos = repositorioMarcas.contarProductos(id);
    if (productos > 0) {
      throw new MarcaConProductosException(marca.nombre(), productos);
    }
    repositorioMarcas.eliminar(marca);
  }
}
