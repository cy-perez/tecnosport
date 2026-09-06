package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.catalogo.SetRotacion;
import java.util.Objects;

/**
 * Primer paso de la captura: abre un set vacío en BORRADOR para un producto existente, prometiendo
 * cuántos fotogramas va a tener. Los fotogramas llegan después, al completar ({@link
 * CompletarSetRotacion}); aquí todavía no hay ninguna imagen.
 */
public final class AbrirSetRotacion {

  private final RepositorioProductos repositorioProductos;
  private final RepositorioSetsRotacion repositorioSetsRotacion;
  private final Reloj reloj;

  public AbrirSetRotacion(
      RepositorioProductos repositorioProductos,
      RepositorioSetsRotacion repositorioSetsRotacion,
      Reloj reloj) {
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
    this.repositorioSetsRotacion = Objects.requireNonNull(repositorioSetsRotacion);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public SetRotacion ejecutar(AbrirSetRotacionComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");

    repositorioProductos
        .buscarPorId(comando.productoId())
        .orElseThrow(() -> new ProductoNoEncontradoPorIdException(comando.productoId()));

    SetRotacion set =
        SetRotacion.abrir(
            comando.productoId(),
            comando.fotogramas(),
            comando.capturadoPor(),
            reloj.ahora(),
            comando.dispositivo(),
            comando.versionAsistente());
    repositorioSetsRotacion.guardar(set);
    return set;
  }
}
