package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.application.compartido.EnTransaccionPropia;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.proveedores.EstadoLote;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.ResumenIngesta;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * El trabajo de fondo de un lote: leerlo, registrar sus mensajes y cerrarlo con su resumen.
 *
 * <p>Corre fuera de una petición, en el hilo del ejecutor, y por eso <b>abre sus propias
 * transacciones</b> con {@code EnTransaccionPropia}: tomar el lote es una, registrar es otra y
 * cerrarlo es la tercera. Entre la primera y la segunda está la lectura del archivo, que puede
 * tardar y no tiene por qué sostener una transacción abierta; y si el registro revienta, el lote
 * tiene que quedar en {@code ERROR} con su motivo, cosa imposible si el error revirtiera también el
 * estado del lote.
 *
 * <p><b>Un lote que no está en la cola no se procesa dos veces.</b> Si el ejecutor lo entrega
 * repetido, o si ya lo tomó otra instancia, se devuelve tal como está.
 *
 * <p>Después de registrar vendrán la agrupación en publicaciones y la extracción, que se enganchan
 * aquí y no en otro sitio: es el único lugar que sabe cuándo un lote está entero.
 */
public final class ProcesarLoteDeIngesta {

  private final RepositorioLotesIngesta repositorioLotes;
  private final FuenteDeMensajes fuente;
  private final RegistrarMensajesDeProveedor registrar;
  private final EnTransaccionPropia enTransaccionPropia;
  private final Reloj reloj;

  public ProcesarLoteDeIngesta(
      RepositorioLotesIngesta repositorioLotes,
      FuenteDeMensajes fuente,
      RegistrarMensajesDeProveedor registrar,
      EnTransaccionPropia enTransaccionPropia,
      Reloj reloj) {
    this.repositorioLotes = Objects.requireNonNull(repositorioLotes);
    this.fuente = Objects.requireNonNull(fuente);
    this.registrar = Objects.requireNonNull(registrar);
    this.enTransaccionPropia = Objects.requireNonNull(enTransaccionPropia);
    this.reloj = Objects.requireNonNull(reloj);
  }

  /**
   * @return el lote como quedó: {@code TERMINADO}, {@code ERROR}, o sin tocar si no estaba en la
   *     cola
   */
  public LoteIngesta ejecutar(UUID loteId) {
    LoteIngesta lote = enTransaccionPropia.ejecutar(() -> tomar(loteId));
    if (lote.estado() != EstadoLote.PROCESANDO) {
      return lote;
    }

    try {
      String referencia =
          lote.referenciaArchivo()
              .orElseThrow(
                  () -> new ExportacionIlegibleException("El lote no tiene archivo que leer."));
      List<MensajeCrudo> crudos = fuente.leer(referencia);
      MensajesRegistrados registrados =
          enTransaccionPropia.ejecutar(() -> registrar.ejecutar(lote.id(), crudos));

      ResumenIngesta resumen =
          new ResumenIngesta(
              registrados.leidos(),
              registrados.ignorados(),
              registrados.cuantosNuevos(),
              0,
              0,
              0,
              0,
              0,
              0);
      return enTransaccionPropia.ejecutar(
          () -> {
            lote.terminar(resumen, reloj.ahora());
            repositorioLotes.actualizar(lote);
            return lote;
          });
    } catch (RuntimeException e) {
      String motivo = motivoLegible(e);
      enTransaccionPropia.ejecutar(
          () -> {
            lote.fallar(motivo, reloj.ahora());
            repositorioLotes.actualizar(lote);
            return lote;
          });
      throw e;
    }
  }

  private LoteIngesta tomar(UUID loteId) {
    LoteIngesta lote =
        repositorioLotes
            .buscarPorId(loteId)
            .orElseThrow(() -> new LoteNoEncontradoException(loteId));
    if (lote.estado() != EstadoLote.RECIBIDO) {
      return lote;
    }
    lote.iniciar(reloj.ahora());
    repositorioLotes.actualizar(lote);
    return lote;
  }

  /**
   * Lo que se escribe en el lote lo lee una persona en el panel. El mensaje de una excepción
   * nuestra está escrito para eso; el de un {@code NullPointerException} no, y se sustituye por
   * algo que al menos diga que fue un fallo del programa. La traza completa la registra quien
   * llama, que es quien tiene el log.
   */
  private static String motivoLegible(RuntimeException e) {
    if (e instanceof ExportacionIlegibleException || e instanceof ExcepcionDeDominio) {
      return e.getMessage();
    }
    return "Error inesperado al procesar el lote (" + e.getClass().getSimpleName() + ").";
  }
}
