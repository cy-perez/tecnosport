package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.application.compartido.EnTransaccionPropia;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.proveedores.AgrupadorDePublicaciones;
import co.tecnosport.api.domain.proveedores.EstadoLote;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import co.tecnosport.api.domain.proveedores.NombreDeChat;
import co.tecnosport.api.domain.proveedores.PHash;
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
 * repetido, o si ya lo tomó otra instancia, se devuelve tal como está. Lo mismo uno que se detuvo
 * mientras esperaba en la cola: el trabajador lo salta.
 *
 * <h2>Pausar y detener</h2>
 *
 * <p>El panel no le habla al hilo: escribe el estado del lote, y el trabajador lo relee en sus
 * puntos de control —después de leer el archivo, después de registrar y antes de cada publicación—.
 * En {@code PAUSADO} espera ahí mismo, sin soltar el hilo; en {@code DETENIENDO} cierra con lo que
 * alcanzó y suelta el hilo para el lote siguiente. Lo que esté haciendo entre dos puntos —una
 * extracción, la subida de las fotos— termina antes de que la orden se note.
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
  private final EsperaDeIngesta espera;
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
      EsperaDeIngesta espera,
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
    this.espera = Objects.requireNonNull(espera);
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

    Progreso progreso = new Progreso();
    try {
      String referencia =
          lote.referenciaArchivo()
              .orElseThrow(
                  () -> new ExportacionIlegibleException("El lote no tiene archivo que leer."));
      ChatExportado chat = fuente.leer(referencia, lote.chatDelZip().orElse(null));
      List<MensajeCrudo> crudos = chat.mensajes();
      // El de un zip de dos chats ya nace marcado; el de un chat suelto se sabe por su nombre.
      boolean deCaballero = lote.esChatDeCaballero() || NombreDeChat.esDeCaballero(chat.nombre());
      if (deCaballero && !lote.esChatDeCaballero()) {
        enTransaccionPropia.ejecutar(
            () -> {
              LoteIngesta fresco = paraActualizar(lote.id());
              fresco.marcarChatDeCaballero();
              repositorioLotes.actualizar(fresco);
              return fresco;
            });
      }
      puntoDeControl(lote.id());
      // Sin transacción a propósito: registrar sube cada foto al bucket, y un lote con dos mil
      // fotos sostendría una conexión durante minutos. Lo único que escribe en la base es un solo
      // guardarTodos, que el adaptador ya hace atómico.
      MensajesRegistrados registrados = registrar.ejecutar(lote.id(), crudos);
      progreso.registrados = registrados;
      puntoDeControl(lote.id());

      AgrupadorDePublicaciones.Resultado agrupado =
          enTransaccionPropia.ejecutar(() -> armar.ejecutar(lote.id()));
      progreso.publicaciones = agrupado.publicaciones().size();

      Proveedor proveedor =
          repositorioProveedores
              .buscarPorId(lote.proveedorId())
              .orElseThrow(() -> new ProveedorNoEncontradoException(lote.proveedorId()));
      Map<UUID, MensajeProveedor> mensajes =
          repositorioMensajes.listarDeLote(lote.id()).stream()
              .collect(Collectors.toMap(MensajeProveedor::id, Function.identity()));

      // Una consulta por lote y no una por publicación: la lista crece con cada renovación.
      List<HuellaVisual> huellasVisuales = resolver.huellasVisualesDe(proveedor.id());
      // Los textos del chat de caballero, una vez por lote: con ellos el chat general descarta lo
      // que ese ya trajo. El propio chat de caballero no los necesita.
      ChatDelLote chatDelLote =
          new ChatDelLote(
              deCaballero,
              deCaballero
                  ? List.of()
                  : repositorioPublicaciones.textosDelChatDeCaballero(proveedor.id()));
      Contador contador = progreso.contador;
      for (PublicacionProveedor publicacion : agrupado.publicaciones()) {
        puntoDeControl(lote.id());
        resolverUna(publicacion, mensajes, proveedor, huellasVisuales, chatDelLote, contador);
      }

      ResumenIngesta resumen = progreso.resumen();
      return enTransaccionPropia.ejecutar(
          () -> {
            // Del lote fresco y bloqueado, no del que se tomó: el panel pudo pausarlo o pedir
            // detenerlo después del último punto de control, y esa escritura no se pisa a ciegas.
            LoteIngesta fresco = paraActualizar(lote.id());
            if (!fresco.enManosDelTrabajador()) {
              return fresco;
            }
            fresco.terminar(resumen, reloj.ahora());
            repositorioLotes.actualizar(fresco);
            return fresco;
          });
    } catch (DetencionPedida detencion) {
      try {
        return enTransaccionPropia.ejecutar(
            () -> {
              LoteIngesta fresco = paraActualizar(lote.id());
              if (fresco.estado() == EstadoLote.DETENIENDO) {
                fresco.detener(progreso.resumen(), reloj.ahora());
                repositorioLotes.actualizar(fresco);
              }
              return fresco;
            });
      } catch (RuntimeException e) {
        // Sin esto, un fallo de la base justo aquí dejaba el lote DETENIENDO —abierto, sin poder
        // eliminarse ni volver a detenerse— hasta el siguiente arranque.
        cerrarConFallo(lote.id(), e);
        throw e;
      }
    } catch (LoteAjeno ajeno) {
      // Ya no es de este hilo: otra instancia lo cerró al arrancar, o se eliminó. No se escribe
      // nada: lo que diga la base es lo que vale.
      return repositorioLotes.buscarPorId(lote.id()).orElse(lote);
    } catch (RuntimeException | Error e) {
      // También los Error: un zip que no cabe en memoria o una foto que revienta el decodificador
      // lanzan OutOfMemoryError, y el lote no puede quedar PROCESANDO para siempre por eso.
      cerrarConFallo(lote.id(), e);
      throw e;
    }
  }

  /**
   * Escribe el fallo sobre el lote releído y bloqueado. Si lo que falló fue el commit de
   * "terminar", el lote en memoria ya dice TERMINADO y no admite fallar; en la base sigue abierto.
   * Y si en la base ya no está —se eliminó—, no se escribe: guardar el de memoria lo volvería a
   * insertar.
   */
  private void cerrarConFallo(UUID loteId, Throwable e) {
    String motivo = motivoLegible(e);
    enTransaccionPropia.ejecutar(
        () -> {
          repositorioLotes
              .buscarPorIdParaActualizar(loteId)
              .filter(LoteIngesta::estaAbierto)
              .ifPresent(
                  fresco -> {
                    fresco.fallar(motivo, reloj.ahora());
                    repositorioLotes.actualizar(fresco);
                  });
          return null;
        });
  }

  /** La extracción fuera de la transacción; la resolución dentro. Un fallo no sale de aquí. */
  private void resolverUna(
      PublicacionProveedor publicacion,
      Map<UUID, MensajeProveedor> mensajes,
      Proveedor proveedor,
      List<HuellaVisual> huellasVisuales,
      ChatDelLote chatDelLote,
      Contador contador) {
    List<ExtraccionEvaluada> evaluadas;
    try {
      evaluadas = extraer.ejecutar(publicacion, mensajes, proveedor.linea());
    } catch (ExtraccionFallidaException e) {
      fallarPublicacion(publicacion, e.getMessage());
      contador.descartes++;
      return;
    }
    try {
      // Las fotos se leen y se decodifican aquí, fuera de la transacción; adentro solo se decide.
      // Con varios productos sin fotos repartidas no se leen: la primera foto puede ser de
      // cualquiera y no se usa.
      Map<UUID, PHash> pHashes =
          ResolverBorrador.necesitaPHashes(evaluadas)
              ? resolver.pHashesDe(publicacion, mensajes)
              : Map.of();
      List<ResolverBorrador.Resolucion> resoluciones =
          enTransaccionPropia.ejecutar(
              () ->
                  resolver.ejecutar(
                      publicacion,
                      mensajes,
                      proveedor,
                      evaluadas,
                      pHashes,
                      huellasVisuales,
                      chatDelLote));
      for (ResolverBorrador.Resolucion resolucion : resoluciones) {
        switch (resolucion.tipo()) {
          case NUEVO -> contador.nuevos++;
          case RENOVACION -> contador.renovaciones++;
          case AGOTADO -> contador.agotados++;
          case DESCARTADA -> contador.descartes++;
        }
        contador.alertas += resolucion.alertas().size();
      }
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

  /**
   * Relee el lote y obedece lo que diga: en pausa espera aquí, tantas veces como haga falta, y si
   * se pidió detenerlo sale con {@link DetencionPedida}. Sin transacción: solo lee.
   *
   * <p>Cualquier otro estado, o ninguno, quiere decir que el lote dejó de ser de este hilo: otra
   * instancia lo cerró con error al arrancar ({@link ReanudarLotesDeIngesta}) o se eliminó después.
   * Seguir sería crear borradores colgados de un lote que el panel ya da por cerrado, y salir de
   * una pausa sin que nadie la reanudara.
   */
  private void puntoDeControl(UUID loteId) {
    while (true) {
      EstadoLote estado =
          repositorioLotes.buscarPorId(loteId).map(LoteIngesta::estado).orElse(null);
      if (estado == EstadoLote.PROCESANDO) {
        return;
      }
      if (estado == EstadoLote.PAUSADO) {
        espera.esperar();
      } else if (estado == EstadoLote.DETENIENDO) {
        throw new DetencionPedida();
      } else {
        throw new LoteAjeno();
      }
    }
  }

  private LoteIngesta paraActualizar(UUID loteId) {
    return repositorioLotes
        .buscarPorIdParaActualizar(loteId)
        .orElseThrow(() -> new LoteNoEncontradoException(loteId));
  }

  private LoteIngesta tomar(UUID loteId) {
    LoteIngesta lote = paraActualizar(loteId);
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
  private static String motivoLegible(Throwable e) {
    if (e instanceof ExportacionIlegibleException
        || e instanceof ExtraccionFallidaException
        || e instanceof IngestaInterrumpidaException
        || e instanceof ExcepcionDeDominio) {
      return e.getMessage();
    }
    return "Error inesperado (" + e.getClass().getSimpleName() + ").";
  }

  /**
   * Lo que va del lote, para poder cerrarlo con cifras también cuando se detiene a la mitad. Las
   * fases que no alcanzaron a correr cuentan cero.
   */
  private static final class Progreso {
    MensajesRegistrados registrados;
    int publicaciones;
    final Contador contador = new Contador();

    ResumenIngesta resumen() {
      return new ResumenIngesta(
          registrados == null ? 0 : registrados.leidos(),
          registrados == null ? 0 : registrados.ignorados(),
          registrados == null ? 0 : registrados.cuantosNuevos(),
          publicaciones,
          contador.nuevos,
          contador.renovaciones,
          contador.agotados,
          contador.descartes,
          contador.alertas);
    }
  }

  /** El panel pidió detener el lote; sale del trabajo sin ser un fallo. */
  private static final class DetencionPedida extends RuntimeException {
    DetencionPedida() {
      super(null, null, false, false);
    }
  }

  /** El lote ya no está en manos de este hilo; sale sin escribir nada. */
  private static final class LoteAjeno extends RuntimeException {
    LoteAjeno() {
      super(null, null, false, false);
    }
  }

  private static final class Contador {
    int nuevos;
    int renovaciones;
    int agotados;
    int descartes;
    int alertas;
  }
}
