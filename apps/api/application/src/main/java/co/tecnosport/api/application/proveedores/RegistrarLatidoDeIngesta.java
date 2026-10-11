package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.application.compartido.Reloj;
import java.util.Objects;
import java.util.UUID;

/**
 * El trabajador avisa que sigue vivo con el lote que tiene. Lo llama un temporizador aparte del
 * hilo que procesa, cada pocos segundos, porque entre dos puntos de control del trabajo —subir dos
 * mil fotos, una extracción con reintentos— pueden pasar minutos. Ver {@link
 * RecuperarIngestasAbandonadas}.
 */
public final class RegistrarLatidoDeIngesta {

  private final RepositorioLotesIngesta repositorioLotes;
  private final Reloj reloj;

  public RegistrarLatidoDeIngesta(RepositorioLotesIngesta repositorioLotes, Reloj reloj) {
    this.repositorioLotes = Objects.requireNonNull(repositorioLotes);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public void ejecutar(UUID loteId) {
    repositorioLotes.registrarLatido(Objects.requireNonNull(loteId), reloj.ahora());
  }
}
