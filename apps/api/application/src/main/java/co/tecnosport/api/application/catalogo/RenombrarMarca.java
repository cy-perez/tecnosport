package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Marca;
import java.util.Objects;
import java.util.UUID;

/**
 * Cambia el nombre de una marca desde el panel.
 *
 * <p>Lo ve el comprador: la ficha de cada producto de la marca y el filtro de la vitrina muestran
 * el nombre nuevo en cuanto se guarda. Lo que no cambia es el slug de los productos, que se fijó al
 * crearlos: renombrar una marca no rompe enlaces ya compartidos. Ver {@code ADR-0076}.
 */
public final class RenombrarMarca {

  private final RepositorioMarcas repositorioMarcas;

  public RenombrarMarca(RepositorioMarcas repositorioMarcas) {
    this.repositorioMarcas =
        Objects.requireNonNull(repositorioMarcas, "El repositorio de marcas no puede ser nulo.");
  }

  public Marca ejecutar(UUID id, String nombre) {
    Objects.requireNonNull(id, "El id no puede ser nulo.");
    Marca actual =
        repositorioMarcas.buscarPorId(id).orElseThrow(() -> new MarcaNoEncontradaException(id));
    // El dominio primero, como en CrearMarca: recorta los espacios antes de preguntar si choca.
    Marca renombrada = actual.renombrar(nombre);

    if (repositorioMarcas.existeOtraConNombre(renombrada.nombre(), id)) {
      throw new MarcaYaExisteException(renombrada.nombre());
    }

    repositorioMarcas.actualizar(renombrada);
    return renombrada;
  }
}
