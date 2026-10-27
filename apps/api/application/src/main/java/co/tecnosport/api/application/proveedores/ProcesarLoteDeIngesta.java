package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.application.compartido.EnTransaccionPropia;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.proveedores.AgrupadorDePublicaciones;
import co.tecnosport.api.domain.proveedores.CierreDePublicacion;
import co.tecnosport.api.domain.proveedores.ColaDelProveedor;
import co.tecnosport.api.domain.proveedores.EstadoLote;
import co.tecnosport.api.domain.proveedores.EstadoPublicacionProveedor;
import co.tecnosport.api.domain.proveedores.FalloDeLote;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import co.tecnosport.api.domain.proveedores.MotivoDeFalloDeLote;
import co.tecnosport.api.domain.proveedores.MotivoDePublicacion;
import co.tecnosport.api.domain.proveedores.NombreDeChat;
import co.tecnosport.api.domain.proveedores.PHash;
import co.tecnosport.api.domain.proveedores.Proveedor;
import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import co.tecnosport.api.domain.proveedores.ResumenIngesta;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * El trabajo de fondo de un lote, <b>por tramos</b> (ADR-0080): leerlo, registrar sus mensajes y
 * armar las publicaciones la primera vez; extraer y resolver las publicaciones pendientes hasta que
 * se acabe el presupuesto del tramo; y cerrarlo con su resumen cuando no quede ninguna.
 *
 * <h2>Por qué en tramos</h2>
 *
 * <p>Una tarea de Cloud Tasks dura a lo más treinta minutos, y un chat grande con la lectura de
 * fotos puede pasar de eso. Al acabarse el presupuesto, el tramo devuelve el lote a la cola con su
 * avance y quien lo llamó encola el siguiente ({@link AvanzarIngesta}). Retomar es posible porque
 * cada publicación guarda su estado: el tramo siguiente sigue con las que están en {@code
 * PENDIENTE_EXTRACCION}. El avance se anota después de cada publicación, para que un tramo cortado
 * no se lleve lo que contó.
 *
 * <p><b>La lectura se hace una vez.</b> Armar las publicaciones va dentro de la transacción que
 * bloquea el lote y anota el avance, y el avance anotado es la marca de que la lectura está hecha:
 * si otro tramo ya armó, este no vuelve a armar. Si el trabajador muere antes de confirmarla, el
 * tramo siguiente lee otra vez: registrar es idempotente y las publicaciones no llegaron a
 * guardarse.
 *
 * <h2>De quién es el lote</h2>
 *
 * <p>El estado no basta: un trabajador congelado puede despertar con el lote en {@code PROCESANDO}
 * después de que otro lo diera por abandonado y lo retomara. Por eso el tramo recuerda los
 * reintentos del lote al tomarlo —recuperarlo los sube— y en cada punto de control y en cada
 * escritura comprueba que sigan siendo los mismos. Si cambiaron, el lote ya es de otro y este sale
 * sin escribir nada.
 *
 * <h2>Cuando algo falla</h2>
 *
 * <p>Un fallo que se repetiría siempre —un zip ilegible, una regla del dominio— cierra el lote con
 * su motivo. Cualquier otro puede ser pasajero, y el lote vuelve a la cola para que otro tramo lo
 * retome, con el mismo tope de reintentos que el de un trabajador muerto. En los dos casos sale
 * {@link TramoFallidoException}, que dice si tiene sentido reintentar.
 *
 * <h2>Pausar y detener</h2>
 *
 * <p>El panel no le habla al trabajador: escribe el estado del lote, y el trabajador lo relee en
 * sus puntos de control —después de leer el archivo, después de registrar y antes de cada
 * publicación—. En {@code PAUSADO} lo suelta con su avance y no encola nada: lo retoma quien lo
 * reanude. En {@code DETENIENDO} lo cierra con lo que alcanzó.
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
  private final Duration presupuesto;
  private final Duration toleranciaDelLatido;

  /**
   * @param presupuesto cuánto trabaja un tramo antes de devolver el lote a la cola. La publicación
   *     que está en curso cuando se acaba termina primero, así que tiene que quedar holgura contra
   *     el plazo de la tarea
   * @param toleranciaDelLatido la misma de {@link RecuperarIngestasAbandonadas}: un tramo que
   *     encuentra el lote abandonado lo retoma él mismo
   */
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
      Reloj reloj,
      Duration presupuesto,
      Duration toleranciaDelLatido) {
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
    this.presupuesto = exigirPositiva(presupuesto, "El presupuesto de un tramo");
    this.toleranciaDelLatido = exigirPositiva(toleranciaDelLatido, "La tolerancia del latido");
  }

  private static Duration exigirPositiva(Duration duracion, String que) {
    Objects.requireNonNull(duracion);
    if (duracion.isNegative() || duracion.isZero()) {
      throw new IllegalArgumentException(que + " tiene que ser positiva.");
    }
    return duracion;
  }

  /**
   * Un lote entero, tramo tras tramo y reintento tras reintento, en el mismo hilo: lo que harían la
   * cola y sus reintentos. Es lo que usan las pruebas.
   *
   * @return el lote como quedó
   * @throws TramoFallidoException si el lote quedó cerrado por un fallo
   */
  public LoteIngesta ejecutar(UUID loteId) {
    while (true) {
      Tramo tramo;
      try {
        tramo = procesarTramo(loteId);
      } catch (TramoFallidoException e) {
        if (e.reintentar()) {
          continue;
        }
        throw e;
      }
      if (tramo.resultado() != Resultado.SIGUE) {
        return tramo.lote();
      }
    }
  }

  /**
   * Un tramo. Si el lote no está en la cola, o tiene que esperar a uno anterior de su proveedor
   * ({@link ColaDelProveedor}), se devuelve tal como está: el tramo que llegó repetido no hace
   * nada, y al que espera lo encola quien suelte al anterior. Si lo tiene otro trabajador vivo, lo
   * dice ({@link Resultado#OCUPADO}); si ese trabajador dejó de latir, lo retoma.
   *
   * @throws LoteNoEncontradoException si el lote no existe
   * @throws TramoFallidoException si algo falló, diciendo si tiene sentido reintentar
   */
  public Tramo procesarTramo(UUID loteId) {
    Toma toma = enTransaccionPropia.ejecutar(() -> tomar(loteId));
    if (toma.resultado() != null) {
      return new Tramo(toma.lote(), toma.resultado());
    }
    LoteIngesta lote = toma.lote();
    int mios = lote.reintentos();

    Instant fin = reloj.ahora().plus(presupuesto);
    Progreso progreso = new Progreso(lote.resumen().orElse(null));
    try {
      if (!lote.lecturaHecha()) {
        leer(lote, mios, progreso);
      }
      Proveedor proveedor =
          repositorioProveedores
              .buscarPorId(lote.proveedorId())
              .orElseThrow(() -> new ProveedorNoEncontradoException(lote.proveedorId()));
      Map<UUID, MensajeProveedor> mensajes =
          repositorioMensajes.listarDeLote(lote.id()).stream()
              .collect(Collectors.toMap(MensajeProveedor::id, Function.identity()));
      List<PublicacionProveedor> pendientes =
          repositorioPublicaciones.listarDeLote(lote.id()).stream()
              .filter(p -> p.estado() == EstadoPublicacionProveedor.PENDIENTE_EXTRACCION)
              .toList();

      // Una consulta por tramo y no una por publicación: la lista crece con cada renovación.
      List<HuellaVisual> huellasVisuales = resolver.huellasVisualesDe(proveedor.id());
      // El marcado ya está en el lote: el primer tramo lo anotó al leer el nombre del chat.
      boolean deCaballero =
          repositorioLotes.buscarPorId(lote.id()).map(LoteIngesta::esChatDeCaballero).orElse(false);
      ChatDelLote chatDelLote =
          new ChatDelLote(
              deCaballero,
              deCaballero
                  ? List.of()
                  : repositorioPublicaciones.textosDelChatDeCaballero(proveedor.id()));
      for (PublicacionProveedor publicacion : pendientes) {
        puntoDeControl(lote.id(), mios);
        if (!reloj.ahora().isBefore(fin)) {
          return soltarParaSeguir(lote.id(), mios, progreso);
        }
        resolverUna(
            lote.id(),
            mios,
            publicacion,
            mensajes,
            proveedor,
            huellasVisuales,
            chatDelLote,
            progreso);
        anotarAvance(lote.id(), mios, progreso);
      }

      ResumenIngesta resumen = progreso.resumen();
      return new Tramo(
          enTransaccionPropia.ejecutar(
              () -> {
                // Del lote fresco y bloqueado, no del que se tomó: el panel pudo pausarlo o pedir
                // detenerlo después del último punto de control, y esa escritura no se pisa a
                // ciegas. Si ya no es de este trabajador, tampoco.
                LoteIngesta fresco = paraActualizar(lote.id());
                if (!esMio(fresco, mios)
                    || (!fresco.enManosDelTrabajador() && fresco.estado() != EstadoLote.PAUSADO)) {
                  return fresco;
                }
                fresco.terminar(resumen, reloj.ahora());
                repositorioLotes.actualizar(fresco);
                return fresco;
              }),
          Resultado.SOLTADO);
    } catch (PausaPedida pausa) {
      return new Tramo(soltarEnPausa(lote.id(), mios, progreso), Resultado.SOLTADO);
    } catch (ExtractorNoDisponibleException e) {
      // La cuenta del modelo, no la publicación: seguir solo convertiría cada una en un error. Se
      // pausa con el motivo y la publicación se queda pendiente para cuando se reanude.
      return new Tramo(pausarPorFallo(lote.id(), mios, progreso, e), Resultado.SOLTADO);
    } catch (DetencionPedida detencion) {
      return new Tramo(detener(lote.id(), mios, progreso), Resultado.SOLTADO);
    } catch (LoteAjeno ajeno) {
      // Ya no es de este trabajador: otro lo retomó o lo cerró, o se eliminó. No se escribe nada:
      // lo que diga la base es lo que vale.
      return new Tramo(repositorioLotes.buscarPorId(lote.id()).orElse(lote), Resultado.NADA);
    } catch (LoteNoEncontradoException e) {
      // Se eliminó mientras trabajaba: no hay nada que cerrar ni que reintentar.
      throw e;
    } catch (ExportacionIlegibleException | ExcepcionDeDominio | ProveedorNoEncontradoException e) {
      // Lo que se repetiría igual en cada intento: reintentar no cambia nada.
      cerrarConFallo(lote.id(), mios, e);
      throw new TramoFallidoException(e, false);
    } catch (RuntimeException | Error e) {
      // Puede ser pasajero —la base, el bucket, la red— o no —un zip que no cabe en memoria—: el
      // lote vuelve a la cola y el tope de reintentos decide. También los Error: el lote no puede
      // quedar PROCESANDO para siempre por un OutOfMemoryError.
      throw new TramoFallidoException(e, devolverParaReintentar(lote.id(), mios, e));
    }
  }

  /**
   * La primera parte del primer tramo: el archivo, los mensajes y las publicaciones. Sin
   * transacción para registrar a propósito: sube cada foto al bucket, y un lote con dos mil fotos
   * sostendría una conexión durante minutos. Lo único que escribe en la base es un solo {@code
   * guardarTodos}, que el adaptador ya hace atómico.
   */
  private void leer(LoteIngesta lote, int mios, Progreso progreso) {
    String referencia =
        lote.referenciaArchivo()
            .orElseThrow(
                () ->
                    new ExportacionIlegibleException(
                        MotivoDeFalloDeLote.SIN_ARCHIVO, "El lote no tiene archivo que leer."));
    ChatExportado chat = fuente.leer(referencia, lote.chatDelZip().orElse(null));
    // El de un zip de dos chats ya nace marcado; el de un chat suelto se sabe por su nombre.
    if (!lote.esChatDeCaballero() && NombreDeChat.esDeCaballero(chat.nombre())) {
      enTransaccionPropia.ejecutar(
          () -> {
            LoteIngesta fresco = mioParaActualizar(lote.id(), mios);
            fresco.marcarChatDeCaballero();
            repositorioLotes.actualizar(fresco);
            return fresco;
          });
    }
    puntoDeControl(lote.id(), mios);
    progreso.registrados = registrar.ejecutar(lote.id(), chat.mensajes());
    puntoDeControl(lote.id(), mios);

    enTransaccionPropia.ejecutar(
        () -> {
          // Primero el bloqueo: si otro tramo ya armó, este no vuelve a armar.
          LoteIngesta fresco = mioParaActualizar(lote.id(), mios);
          if (fresco.lecturaHecha()) {
            progreso.base = fresco.resumen().orElseThrow();
            return fresco;
          }
          AgrupadorDePublicaciones.Resultado agrupado = armar.ejecutar(lote.id());
          progreso.publicaciones = agrupado.publicaciones().size();
          progreso.base = progreso.lectura();
          fresco.anotarAvance(progreso.base);
          repositorioLotes.actualizar(fresco);
          return fresco;
        });
  }

  /** Lo que va, después de cada publicación: un tramo cortado no se lleva lo que contó. */
  private void anotarAvance(UUID loteId, int mios, Progreso progreso) {
    enTransaccionPropia.ejecutar(
        () -> {
          // Si se eliminó mientras trabajaba, ya no es de nadie: el punto de control siguiente
          // sale sin escribir, como con uno que retomó otro.
          repositorioLotes
              .buscarPorIdParaActualizar(loteId)
              .filter(fresco -> esMio(fresco, mios) && fresco.estaAbierto())
              .ifPresent(
                  fresco -> {
                    fresco.anotarAvance(progreso.resumen());
                    repositorioLotes.actualizar(fresco);
                  });
          return null;
        });
  }

  /**
   * Se acabó el presupuesto. Lo normal es devolverlo a la cola; pero si el panel lo pausó o pidió
   * detenerlo justo después del último punto de control, se obedece eso, y entonces no queda
   * trabajo que encolar.
   */
  private Tramo soltarParaSeguir(UUID loteId, int mios, Progreso progreso) {
    return enTransaccionPropia.ejecutar(
        () -> {
          LoteIngesta fresco = paraActualizar(loteId);
          if (!esMio(fresco, mios)) {
            return new Tramo(fresco, Resultado.NADA);
          }
          switch (fresco.estado()) {
            case PROCESANDO -> {
              fresco.soltarParaSeguir(progreso.resumen(), reloj.ahora());
              repositorioLotes.actualizar(fresco);
              return new Tramo(fresco, Resultado.SIGUE);
            }
            case PAUSADO -> {
              fresco.anotarAvance(progreso.resumen());
              repositorioLotes.actualizar(fresco);
              return new Tramo(fresco, Resultado.SOLTADO);
            }
            case DETENIENDO -> {
              fresco.detener(progreso.resumen(), reloj.ahora());
              repositorioLotes.actualizar(fresco);
              return new Tramo(fresco, Resultado.SOLTADO);
            }
            default -> {
              return new Tramo(fresco, Resultado.NADA);
            }
          }
        });
  }

  private LoteIngesta soltarEnPausa(UUID loteId, int mios, Progreso progreso) {
    return enTransaccionPropia.ejecutar(
        () -> {
          LoteIngesta fresco = paraActualizar(loteId);
          if (esMio(fresco, mios)
              && fresco.estado() == EstadoLote.PAUSADO
              && progreso.base != null) {
            fresco.anotarAvance(progreso.resumen());
            repositorioLotes.actualizar(fresco);
          }
          return fresco;
        });
  }

  private LoteIngesta pausarPorFallo(
      UUID loteId, int mios, Progreso progreso, ExtractorNoDisponibleException causa) {
    return enTransaccionPropia.ejecutar(
        () -> {
          LoteIngesta fresco = paraActualizar(loteId);
          if (!esMio(fresco, mios)) {
            return fresco;
          }
          if (fresco.estado() == EstadoLote.DETENIENDO) {
            // Pidieron detenerlo: se obedece eso, con lo que iba.
            fresco.detener(progreso.resumen(), reloj.ahora());
          } else {
            fresco.pausarPorFallo(
                new FalloDeLote(
                    MotivoDeFalloDeLote.EXTRACTOR_NO_DISPONIBLE, String.valueOf(causa.estado())),
                progreso.resumen());
          }
          repositorioLotes.actualizar(fresco);
          return fresco;
        });
  }

  private LoteIngesta detener(UUID loteId, int mios, Progreso progreso) {
    return enTransaccionPropia.ejecutar(
        () -> {
          LoteIngesta fresco = paraActualizar(loteId);
          if (esMio(fresco, mios) && fresco.estado() == EstadoLote.DETENIENDO) {
            fresco.detener(progreso.resumen(), reloj.ahora());
            repositorioLotes.actualizar(fresco);
          }
          return fresco;
        });
  }

  /**
   * Escribe el fallo sobre el lote releído y bloqueado, si sigue abierto y es de este trabajador.
   * Si en la base ya no está —se eliminó—, no se escribe: guardar el de memoria lo volvería a
   * insertar.
   */
  private void cerrarConFallo(UUID loteId, int mios, Throwable e) {
    FalloDeLote fallo = falloDe(e);
    enTransaccionPropia.ejecutar(
        () -> {
          repositorioLotes
              .buscarPorIdParaActualizar(loteId)
              .filter(fresco -> esMio(fresco, mios) && fresco.estaAbierto())
              .ifPresent(
                  fresco -> {
                    fresco.fallar(fallo, reloj.ahora());
                    repositorioLotes.actualizar(fresco);
                  });
          return null;
        });
  }

  /**
   * Devuelve el lote a la cola para que otro tramo lo retome, contando un reintento.
   *
   * @return si quedó en la cola; si se acabaron los reintentos o se estaba deteniendo, quedó
   *     cerrado
   */
  private boolean devolverParaReintentar(UUID loteId, int mios, Throwable e) {
    MotivoDeFalloDeLote motivo = falloDe(e).motivo();
    return enTransaccionPropia.ejecutar(
        () ->
            repositorioLotes
                .buscarPorIdParaActualizar(loteId)
                .filter(fresco -> esMio(fresco, mios) && fresco.enManosDelTrabajador())
                .map(
                    fresco -> {
                      LoteIngesta.Recuperacion recuperacion =
                          fresco.recuperar(reloj.ahora(), motivo);
                      repositorioLotes.actualizar(fresco);
                      return recuperacion == LoteIngesta.Recuperacion.RETOMADO;
                    })
                .orElse(false));
  }

  /**
   * La extracción fuera de la transacción; la resolución dentro. Un fallo no sale de aquí, salvo
   * que el lote haya dejado de ser de este trabajador mientras extraía: eso sale con {@link
   * LoteAjeno}, como en {@link #puntoDeControl}.
   *
   * <p>La extracción puede tardar minutos, y en ese rato otro trabajador pudo retomar el lote y
   * resolver esta misma publicación (27 de octubre de 2026). Por eso la transacción que resuelve
   * bloquea el lote, comprueba que sigue siendo de este trabajador, y relee la publicación
   * bloqueada: si ya no está pendiente de extracción, no hace nada. Pausado o deteniéndose, en
   * cambio, sigue siendo suyo y la extracción ya se pagó: se resuelve, y el punto de control
   * siguiente obedece.
   */
  private void resolverUna(
      UUID loteId,
      int mios,
      PublicacionProveedor publicacion,
      Map<UUID, MensajeProveedor> mensajes,
      Proveedor proveedor,
      List<HuellaVisual> huellasVisuales,
      ChatDelLote chatDelLote,
      Progreso progreso) {
    List<ExtraccionEvaluada> evaluadas;
    try {
      evaluadas =
          extraer.ejecutar(
              publicacion,
              mensajes,
              proveedor.linea(),
              proveedor.ajustesDeIngesta().unaFotoUnProducto());
    } catch (ExtraccionFallidaException e) {
      fallarPublicacion(
          loteId,
          mios,
          publicacion,
          CierreDePublicacion.de(MotivoDePublicacion.EXTRACCION_FALLIDA),
          e.getMessage());
      progreso.fallidas++;
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
              () -> {
                exigirMio(loteId, mios);
                Optional<PublicacionProveedor> pendiente = pendienteParaActualizar(publicacion);
                if (pendiente.isEmpty()) {
                  return List.<ResolverBorrador.Resolucion>of();
                }
                return resolver.ejecutar(
                    pendiente.get(),
                    mensajes,
                    proveedor,
                    evaluadas,
                    pHashes,
                    huellasVisuales,
                    chatDelLote);
              });
      for (ResolverBorrador.Resolucion resolucion : resoluciones) {
        switch (resolucion.tipo()) {
          case NUEVO -> progreso.nuevos++;
          case RENOVACION -> progreso.renovaciones++;
          case AGOTADO -> progreso.agotados++;
          case DESCARTADA -> progreso.descartes++;
        }
        progreso.alertas += resolucion.alertas().size();
      }
    } catch (LoteAjeno e) {
      throw e;
    } catch (RuntimeException e) {
      fallarPublicacion(
          loteId,
          mios,
          publicacion,
          new CierreDePublicacion(
              MotivoDePublicacion.ERROR_AL_RESOLVER, e.getClass().getSimpleName()),
          motivoLegible(e));
      progreso.fallidas++;
    }
  }

  /**
   * Con las mismas guardas que la resolución: si el lote ya no es de este trabajador, o la
   * publicación ya no está pendiente, no escribe nada.
   */
  private void fallarPublicacion(
      UUID loteId,
      int mios,
      PublicacionProveedor publicacion,
      CierreDePublicacion cierre,
      String detalle) {
    enTransaccionPropia.ejecutar(
        () -> {
          if (!sigueSiendoMio(loteId, mios)) {
            return null;
          }
          // La publicación pudo quedar tocada en memoria por la transacción que revirtió; se relee.
          Optional<PublicacionProveedor> fresca = pendienteParaActualizar(publicacion);
          if (fresca.isEmpty()) {
            return null;
          }
          fresca.get().fallar(cierre, detalle);
          repositorioPublicaciones.actualizar(fresca.get());
          return fresca.get();
        });
  }

  /**
   * Dentro de la transacción que resuelve, con el lote bloqueado: sigue siendo de este trabajador,
   * o sale con {@link LoteAjeno}.
   */
  private void exigirMio(UUID loteId, int mios) {
    if (!sigueSiendoMio(loteId, mios)) {
      throw new LoteAjeno();
    }
  }

  /**
   * De este trabajador y sin soltar: en PROCESANDO, deteniéndose o pausado —la pausa la obedece el
   * punto de control siguiente—. La misma condición que la escritura final del tramo.
   */
  private boolean sigueSiendoMio(UUID loteId, int mios) {
    // Eliminado mientras extraía, tampoco: no hay a quién anotarle nada.
    return repositorioLotes
        .buscarPorIdParaActualizar(loteId)
        .filter(
            fresco ->
                esMio(fresco, mios)
                    && (fresco.enManosDelTrabajador() || fresco.estado() == EstadoLote.PAUSADO))
        .isPresent();
  }

  /** La publicación releída con bloqueo, si sigue pendiente de extracción. */
  private Optional<PublicacionProveedor> pendienteParaActualizar(PublicacionProveedor publicacion) {
    return repositorioPublicaciones
        .buscarPorIdParaActualizar(publicacion.id())
        .filter(p -> p.estado() == EstadoPublicacionProveedor.PENDIENTE_EXTRACCION);
  }

  /**
   * Relee el lote y obedece lo que diga: en pausa sale con {@link PausaPedida}, y si se pidió
   * detenerlo, con {@link DetencionPedida}. Sin transacción: solo lee.
   *
   * <p>Si dejó de ser de este trabajador —otro lo retomó o lo cerró, o se eliminó— sale con {@link
   * LoteAjeno}. Seguir sería crear borradores que otro tramo también va a crear.
   */
  private void puntoDeControl(UUID loteId, int mios) {
    LoteIngesta lote = repositorioLotes.buscarPorId(loteId).orElse(null);
    if (lote == null || !esMio(lote, mios)) {
      throw new LoteAjeno();
    }
    switch (lote.estado()) {
      case PROCESANDO -> {}
      case PAUSADO -> throw new PausaPedida();
      case DETENIENDO -> throw new DetencionPedida();
      default -> throw new LoteAjeno();
    }
  }

  /** Sigue siendo del trabajador que lo tomó: recuperarlo sube los reintentos. */
  private static boolean esMio(LoteIngesta lote, int mios) {
    return lote.reintentos() == mios;
  }

  private LoteIngesta paraActualizar(UUID loteId) {
    return repositorioLotes
        .buscarPorIdParaActualizar(loteId)
        .orElseThrow(() -> new LoteNoEncontradoException(loteId));
  }

  private LoteIngesta mioParaActualizar(UUID loteId, int mios) {
    LoteIngesta fresco = paraActualizar(loteId);
    if (!esMio(fresco, mios) || fresco.estado() != EstadoLote.PROCESANDO) {
      throw new LoteAjeno();
    }
    return fresco;
  }

  /** Toma el lote para este tramo. Con {@code resultado} nulo, lo tomó; si no, dice por qué no. */
  private Toma tomar(UUID loteId) {
    LoteIngesta lote = paraActualizar(loteId);
    if (lote.enManosDelTrabajador()) {
      if (!lote.abandonado(reloj.ahora(), toleranciaDelLatido)) {
        return new Toma(lote, Resultado.OCUPADO);
      }
      // Su trabajador murió: en vez de esperar a la tarea periódica —que en dev solo corre con
      // tráfico—, este tramo lo recupera y, si volvió a la cola, lo toma.
      LoteIngesta.Recuperacion recuperacion = lote.recuperar(reloj.ahora());
      repositorioLotes.actualizar(lote);
      if (recuperacion != LoteIngesta.Recuperacion.RETOMADO) {
        return new Toma(lote, Resultado.SOLTADO);
      }
    }
    if (lote.estado() != EstadoLote.RECIBIDO
        || ColaDelProveedor.tieneQueEsperar(lote, repositorioLotes.abiertos())) {
      return new Toma(lote, Resultado.NADA);
    }
    lote.iniciar(reloj.ahora());
    repositorioLotes.actualizar(lote);
    return new Toma(lote, null);
  }

  /**
   * Por qué falló el lote, como código que el panel traduce (regla dura 4). El mensaje de la
   * excepción no va al lote: con la traza completa lo registra quien llama, que es quien tiene el
   * log.
   */
  private static FalloDeLote falloDe(Throwable e) {
    if (e instanceof ExportacionIlegibleException ilegible) {
      return ilegible.fallo();
    }
    if (e instanceof ProveedorNoEncontradoException) {
      return FalloDeLote.de(MotivoDeFalloDeLote.PROVEEDOR_NO_ENCONTRADO);
    }
    return new FalloDeLote(MotivoDeFalloDeLote.ERROR_INESPERADO, e.getClass().getSimpleName());
  }

  /**
   * Lo que se escribe en la publicación lo lee una persona en el panel. El mensaje de una excepción
   * nuestra está escrito para eso; el de un {@code NullPointerException} no, y se sustituye por
   * algo que al menos diga que fue un fallo del programa. Es el detalle para quien diagnostica: lo
   * que el panel muestra es el código del cierre.
   */
  private static String motivoLegible(Throwable e) {
    if (e instanceof ExportacionIlegibleException
        || e instanceof ExtraccionFallidaException
        || e instanceof ExcepcionDeDominio) {
      return e.getMessage();
    }
    return "Error inesperado (" + e.getClass().getSimpleName() + ").";
  }

  /** Cómo terminó un tramo, para quien decide qué encolar después. */
  public enum Resultado {
    /** Volvió a la cola con publicaciones pendientes: hay que encolarlo otra vez. */
    SIGUE,
    /** Este tramo lo soltó: terminó, se detuvo, se pausó o quedó cerrado. */
    SOLTADO,
    /** No había nada que hacer: no estaba en la cola, esperaba a otro, o ya era de otro. */
    NADA,
    /** Lo tiene otro trabajador que sigue latiendo. */
    OCUPADO
  }

  /**
   * Lo que pasó en un tramo.
   *
   * @param lote como quedó
   */
  public record Tramo(LoteIngesta lote, Resultado resultado) {

    public Tramo {
      Objects.requireNonNull(lote);
      Objects.requireNonNull(resultado);
    }

    public boolean quedaTrabajo() {
      return resultado == Resultado.SIGUE;
    }
  }

  private record Toma(LoteIngesta lote, Resultado resultado) {}

  /**
   * Lo que va del lote: lo de los tramos anteriores ({@code base}, que ya trae la lectura) más lo
   * de este. Antes de que la lectura termine, lo que se alcanzó a contar de ella.
   */
  private static final class Progreso {
    ResumenIngesta base;
    MensajesRegistrados registrados;
    int publicaciones;
    int nuevos;
    int renovaciones;
    int agotados;
    int descartes;
    int alertas;
    int fallidas;

    Progreso(ResumenIngesta base) {
      this.base = base;
    }

    ResumenIngesta lectura() {
      return new ResumenIngesta(
          registrados == null ? 0 : registrados.leidos(),
          registrados == null ? 0 : registrados.ignorados(),
          registrados == null ? 0 : registrados.cuantosNuevos(),
          publicaciones,
          0,
          0,
          0,
          0,
          0,
          0);
    }

    ResumenIngesta resumen() {
      ResumenIngesta deEsteTramo =
          new ResumenIngesta(
              0, 0, 0, 0, nuevos, renovaciones, agotados, descartes, alertas, fallidas);
      return (base != null ? base : lectura()).mas(deEsteTramo);
    }
  }

  /** El panel pidió pausar el lote; el tramo lo suelta sin ser un fallo. */
  private static final class PausaPedida extends RuntimeException {
    PausaPedida() {
      super(null, null, false, false);
    }
  }

  /** El panel pidió detener el lote; sale del trabajo sin ser un fallo. */
  private static final class DetencionPedida extends RuntimeException {
    DetencionPedida() {
      super(null, null, false, false);
    }
  }

  /** El lote ya no está en manos de este trabajador; sale sin escribir nada. */
  private static final class LoteAjeno extends RuntimeException {
    LoteAjeno() {
      super(null, null, false, false);
    }
  }
}
