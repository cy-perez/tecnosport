package co.tecnosport.api.application.usuario;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.usuario.SesionRefresco;
import java.util.Objects;

/** Idempotente: cerrar una sesión que ya no existe (o ya estaba cerrada) no es un error. */
public final class CerrarSesion {

  private final RepositorioSesiones repositorioSesiones;
  private final Reloj reloj;

  public CerrarSesion(RepositorioSesiones repositorioSesiones, Reloj reloj) {
    this.repositorioSesiones = Objects.requireNonNull(repositorioSesiones);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public void ejecutar(CerrarSesionComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    SesionRefresco sesion = repositorioSesiones.buscarPorId(comando.refreshTokenId()).orElse(null);
    if (sesion == null) {
      return;
    }
    repositorioSesiones.revocarFamilia(sesion.familiaId(), reloj.ahora());
  }
}
