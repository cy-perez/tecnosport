package co.tecnosport.api.application.compartido;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Doble escrito a mano, sin Mockito (docs/06-testing.md).
 *
 * <p><b>Su reclamo no es un {@code Set.add()}</b>, y la distinción no es teórica: {@code adr/0044}
 * dejó escrito que el doble cuyo reclamo atómico era exactamente eso pasaba igual con el SQL
 * borrado. Aquí el reclamo reproduce la condición real —solo se gana si el correo sigue sin enviar
 * y su próximo intento ya venció— para que una prueba de aplicación no pueda afirmar algo que la
 * sentencia de verdad no garantiza. Lo que esto <b>no</b> puede probar es la atomicidad entre
 * instancias; eso vive en {@code RepositorioCorreosPendientesJpaTest}, contra Postgres.
 */
public final class RepositorioCorreosPendientesFalso implements RepositorioCorreosPendientes {

  /** Lo que de verdad hay en la tabla, que es más de lo que ve el puerto. */
  public record Fila(
      CorreoPendiente correo, Instant proximoIntentoEn, Instant enviadoEn, String ultimoError) {}

  private final Map<UUID, Fila> filas = new LinkedHashMap<>();
  private final List<Instant> proximosIntentosReclamados = new ArrayList<>();
  private boolean pierdeElReclamo;

  public UUID encolar(CorreoPendiente correo, Instant proximoIntentoEn) {
    filas.put(correo.id(), new Fila(correo, proximoIntentoEn, null, null));
    return correo.id();
  }

  /** Simula que otra instancia se adelantó. */
  public void queOtroGaneElReclamo() {
    this.pierdeElReclamo = true;
  }

  @Override
  public List<CorreoPendiente> buscarEnviables(int maxIntentos, Instant ahora, int limite) {
    return filas.values().stream()
        .filter(f -> f.enviadoEn() == null)
        .filter(f -> f.correo().intentos() < maxIntentos)
        .filter(f -> !f.proximoIntentoEn().isAfter(ahora))
        .limit(limite)
        .map(Fila::correo)
        .toList();
  }

  @Override
  public boolean reclamar(UUID id, Instant ahora, Instant proximoIntento) {
    proximosIntentosReclamados.add(proximoIntento);
    if (pierdeElReclamo) {
      return false;
    }
    Fila fila = filas.get(id);
    if (fila == null || fila.enviadoEn() != null || fila.proximoIntentoEn().isAfter(ahora)) {
      return false;
    }
    CorreoPendiente correo = fila.correo();
    filas.put(
        id,
        new Fila(
            new CorreoPendiente(
                correo.id(),
                correo.destinatario(),
                correo.asunto(),
                correo.cuerpoHtml(),
                correo.intentos() + 1),
            proximoIntento,
            null,
            fila.ultimoError()));
    return true;
  }

  @Override
  public void marcarEnviado(UUID id, Instant ahora) {
    Fila fila = filas.get(id);
    filas.put(id, new Fila(fila.correo(), fila.proximoIntentoEn(), ahora, fila.ultimoError()));
  }

  @Override
  public void registrarFallo(UUID id, String detalle) {
    Fila fila = filas.get(id);
    filas.put(id, new Fila(fila.correo(), fila.proximoIntentoEn(), fila.enviadoEn(), detalle));
  }

  @Override
  public int purgarEnviados(Instant limite) {
    List<UUID> aBorrar =
        filas.values().stream()
            .filter(f -> f.enviadoEn() != null && f.enviadoEn().isBefore(limite))
            .map(f -> f.correo().id())
            .toList();
    aBorrar.forEach(filas::remove);
    return aBorrar.size();
  }

  public Fila fila(UUID id) {
    return filas.get(id);
  }

  public List<Instant> proximosIntentosReclamados() {
    return List.copyOf(proximosIntentosReclamados);
  }
}
