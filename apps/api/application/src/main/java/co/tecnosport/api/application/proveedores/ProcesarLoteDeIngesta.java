package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.application.compartido.EnTransaccionPropia;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.proveedores.AgrupadorDePublicaciones;
import co.tecnosport.api.domain.proveedores.EstadoLote;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import co.tecnosport.api.domain.proveedores.Proveedor;
import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import co.tecnosport.api.domain.proveedores.ResumenIngesta;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * El trabajo de fondo de un lote, entero: leerlo, registrar sus mensajes, armar las publicaciones,
 * extraer cada una y resolverla, y cerrarlo con su resumen.
 *
 * <p>Corre fuera de una petición, en el hilo del ejecutor, y por eso <b>abre sus propias
 * transacciones</b> con {@code EnTransaccionPropia}, cortas y una por paso: tomar el lote,
 * registrar, armar, y una por publicación al resolverla. La extracción —la llamada al modelo— va
 * <em>fuera</em> de cualquier transacción: tarda segundos y no tiene por qué sostener una conexión
 * abierta. Y si una publicación revienta, queda en {@code ERROR} con su motivo y las demás siguen:
 * una publicación no tumba el lote.
 *
 * <p><b>Un lote que no está en la cola no se procesa dos veces.</b> Si el ejecutor lo entrega
 * repetido, o si ya lo tomó otra instancia, se devuelve tal como está.
 */
public final class ProcesarLoteDeIngesta {

  private final RepositorioLotesIngesta repositorioLotes;
  private final RepositorioProveedores repositorioProveedores;
  private final RepositorioMensajesProveedor repositorioMensajes;
  private final RepositorioPublicacionesProveedor repositorioPublicaciones;
  private final FuenteDeMensajes fuente;
  private final RegistrarMensajesDeProveedor registrar;
  private final ArmarPublicaciones armar;
  private final ExtraerProductoDePublicacion extraer;
  private final ResolverBorrador resolver;
  private final EnTransaccionPropia enTransaccionPropia;
  private final Reloj reloj;

  public ProcesarLoteDeIngesta(
      RepositorioLotesIngesta repositorioLotes,
      RepositorioProveedores repositorioProveedores,
      RepositorioMensajesProveedor repositorioMensajes,
      RepositorioPublicacionesProveedor repositorioPublicaciones,
      FuenteDeMensajes fuente,
      RegistrarMensajesDeProveedor registrar,
      ArmarPublicaciones armar,
      ExtraerProductoDePublicacion extraer,
      ResolverBorrador resolver,
      EnTransaccionPropia enTransaccionPropia,
      Reloj reloj) {
    this.repositorioLotes = Objects.requireNonNull(repositorioLotes);
    this.repositorioProveedores = Objects.requireNonNull(repositorioProveedores);
    this.repositorioMensajes = Objects.requireNonNull(repositorioMensajes);
    this.repositorioPublicaciones = Objects.requireNonNull(repositorioPublicaciones);
    this.fuente = Objects.requireNonNull(fuente);
    this.registrar = Objects.requireNonNull(registrar);
    this.armar = Objects.requireNonNull(armar);
    this.extraer = Objects.requireNonNull(extraer);
    this.resolver = Objects.requireNonNull(resolver);
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

      AgrupadorDePublicaciones.Resultado agrupado =
          enTransaccionPropia.ejecutar(() -> armar.ejecutar(lote.id()));

      Proveedor proveedor =
          repositorioProveedores
              .buscarPorId(lote.proveedorId())
              .orElseThrow(() -> new ProveedorNoEncontradoException(lote.proveedorId()));
      Map<UUID, MensajeProveedor> mensajes =
          repositorioMensajes.listarDeLote(lote.id()).stream()
              .collect(Collectors.toMap(MensajeProveedor::id, Function.identity()));

      Contador contador = new Contador();
      for (PublicacionProveedor publicacion : agrupado.publicaciones()) {
        resolverUna(publicacion, mensajes, proveedor, contador);
      }

      ResumenIngesta resumen =
          new ResumenIngesta(
              registrados.leidos(),
              registrados.ignorados(),
              registrados.cuantosNuevos(),
              agrupado.publicaciones().size(),
              contador.nuevos,
              contador.renovaciones,
              contador.agotados,
              contador.descartes,
              contador.alertas);
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

  /** La extracción fuera de la transacción; la resolución dentro. Un fallo no sale de aquí. */
  private void resolverUna(
      PublicacionProveedor publicacion,
      Map<UUID, MensajeProveedor> mensajes,
      Proveedor proveedor,
      Contador contador) {
    ExtraccionEvaluada evaluada;
    try {
      evaluada = extraer.ejecutar(publicacion, mensajes, proveedor.linea());
    } catch (ExtraccionFallidaException e) {
      fallarPublicacion(publicacion, e.getMessage());
      contador.descartes++;
      return;
    }
    try {
      ResolverBorrador.Resolucion resolucion =
          enTransaccionPropia.ejecutar(
              () -> resolver.ejecutar(publicacion, mensajes, proveedor, evaluada));
      switch (resolucion.tipo()) {
        case NUEVO -> contador.nuevos++;
        case RENOVACION -> contador.renovaciones++;
        case AGOTADO -> contador.agotados++;
        case DESCARTADA -> contador.descartes++;
      }
      contador.alertas += resolucion.alertas().size();
    } catch (RuntimeException e) {
      fallarPublicacion(publicacion, motivoLegible(e));
      contador.descartes++;
    }
  }

  private void fallarPublicacion(PublicacionProveedor publicacion, String motivo) {
    enTransaccionPropia.ejecutar(
        () -> {
          // La publicación pudo quedar tocada en memoria por la transacción que revirtió; se relee.
          PublicacionProveedor fresca =
              repositorioPublicaciones.buscarPorId(publicacion.id()).orElse(publicacion);
          fresca.fallar(motivo);
          repositorioPublicaciones.actualizar(fresca);
          return fresca;
        });
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
   * Lo que se escribe en el lote o en la publicación lo lee una persona en el panel. El mensaje de
   * una excepción nuestra está escrito para eso; el de un {@code NullPointerException} no, y se
   * sustituye por algo que al menos diga que fue un fallo del programa. La traza completa la
   * registra quien llama, que es quien tiene el log.
   */
  private static String motivoLegible(RuntimeException e) {
    if (e instanceof ExportacionIlegibleException
        || e instanceof ExtraccionFallidaException
        || e instanceof ExcepcionDeDominio) {
      return e.getMessage();
    }
    return "Error inesperado (" + e.getClass().getSimpleName() + ").";
  }

  private static final class Contador {
    int nuevos;
    int renovaciones;
    int agotados;
    int descartes;
    int alertas;
  }
}
