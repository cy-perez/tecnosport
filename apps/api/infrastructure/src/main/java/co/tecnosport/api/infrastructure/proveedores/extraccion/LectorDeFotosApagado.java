package co.tecnosport.api.infrastructure.proveedores.extraccion;

import co.tecnosport.api.application.proveedores.FotosParaLeer;
import co.tecnosport.api.application.proveedores.LectorDeFotos;
import co.tecnosport.api.domain.proveedores.LecturaDeFotos;
import java.util.Optional;

/**
 * El lector cuando no hay clave de la API o la lectura de fotos está apagada: no lee nada, y la
 * publicación se reparte como antes de existir el lector —todas las fotos para todos—.
 */
public final class LectorDeFotosApagado implements LectorDeFotos {

  @Override
  public Optional<LecturaDeFotos> leer(FotosParaLeer fotos) {
    return Optional.empty();
  }
}
