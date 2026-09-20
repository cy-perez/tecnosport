package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Marca;
import java.util.Objects;

/**
 * Alta de una marca desde el panel.
 *
 * <p>Hasta hoy las marcas solo entraban por migración ({@code V54} cargó las doce del negocio), y
 * ese razonamiento sigue en pie para lo que <b>toda instalación necesita</b> — las categorías de
 * {@code V38} siguen ahí. Lo que cambia es el caso que aquel razonamiento no cubría: la marca
 * trece, la que llega en la lista del proveedor del lunes. Obligar a escribir SQL y desplegar para
 * poder cargar un producto convierte un dato operativo en un cambio de esquema. Ver {@code
 * ADR-0047}.
 *
 * <p>No hay renombrar ni borrar, y es deliberado: renombrar cambia lo que ve el comprador en la
 * ficha y en el filtro, y borrar tiene que decidir qué pasa con los productos que cuelgan de la
 * marca. Ninguna de las dos hace falta para cargar catálogo.
 */
public final class CrearMarca {

  private final RepositorioMarcas repositorioMarcas;

  public CrearMarca(RepositorioMarcas repositorioMarcas) {
    this.repositorioMarcas =
        Objects.requireNonNull(repositorioMarcas, "El repositorio de marcas no puede ser nulo.");
  }

  public Marca ejecutar(String nombre) {
    /*
     * El dominio primero, y el orden importa: Marca.crear recorta los espacios, así que preguntar
     * por el nombre antes de construirla dejaría entrar " Xiaomi " como si fuera una marca nueva.
     */
    Marca marca = Marca.crear(nombre);

    if (repositorioMarcas.existeConNombre(marca.nombre())) {
      throw new MarcaYaExisteException(marca.nombre());
    }

    repositorioMarcas.guardar(marca);
    return marca;
  }
}
