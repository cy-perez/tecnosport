package co.tecnosport.api.bootstrap.proveedores;

import co.tecnosport.api.application.catalogo.EliminarProducto;
import co.tecnosport.api.application.compartido.EnTransaccionPropia;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.proveedores.AlmacenDeArchivosDeProveedor;
import co.tecnosport.api.application.proveedores.ArmarPublicaciones;
import co.tecnosport.api.application.proveedores.AvanzarIngesta;
import co.tecnosport.api.application.proveedores.BorrarArchivoDeIngesta;
import co.tecnosport.api.application.proveedores.CalculadorDePHash;
import co.tecnosport.api.application.proveedores.CrearProveedor;
import co.tecnosport.api.application.proveedores.DetenerIngesta;
import co.tecnosport.api.application.proveedores.EditarProveedor;
import co.tecnosport.api.application.proveedores.EjecutorDeIngestas;
import co.tecnosport.api.application.proveedores.EliminacionDeProductos;
import co.tecnosport.api.application.proveedores.EliminacionDeProductosDelCatalogo;
import co.tecnosport.api.application.proveedores.EliminarLoteDeIngesta;
import co.tecnosport.api.application.proveedores.EliminarProveedor;
import co.tecnosport.api.application.proveedores.EncolarIngesta;
import co.tecnosport.api.application.proveedores.ExtraerProductoDePublicacion;
import co.tecnosport.api.application.proveedores.FuenteDeMensajes;
import co.tecnosport.api.application.proveedores.IniciarIngesta;
import co.tecnosport.api.application.proveedores.MarcapasosDeIngesta;
import co.tecnosport.api.application.proveedores.PausarIngesta;
import co.tecnosport.api.application.proveedores.ProcesarLoteDeIngesta;
import co.tecnosport.api.application.proveedores.ReanudarIngesta;
import co.tecnosport.api.application.proveedores.ReanudarLotesDeIngesta;
import co.tecnosport.api.application.proveedores.RecuperarIngestasAbandonadas;
import co.tecnosport.api.application.proveedores.RegistrarLatidoDeIngesta;
import co.tecnosport.api.application.proveedores.RegistrarMensajesDeProveedor;
import co.tecnosport.api.application.proveedores.RepositorioArchivosDeIngesta;
import co.tecnosport.api.application.proveedores.RepositorioLotesIngesta;
import co.tecnosport.api.application.proveedores.RepositorioMensajesProveedor;
import co.tecnosport.api.application.proveedores.RepositorioProveedores;
import co.tecnosport.api.application.proveedores.RepositorioPublicacionesProveedor;
import co.tecnosport.api.application.proveedores.ResolverBorrador;
import co.tecnosport.api.application.proveedores.SolicitarSubidaDeExportacion;
import co.tecnosport.api.application.proveedores.VerificadorDeTareasDeIngesta;
import co.tecnosport.api.infrastructure.proveedores.AlmacenDeArchivosDeProveedorGcs;
import co.tecnosport.api.infrastructure.proveedores.tareas.ColaDeIngestasCloudTasks;
import co.tecnosport.api.infrastructure.proveedores.tareas.TokenDeAccesoDeGoogle;
import co.tecnosport.api.infrastructure.proveedores.tareas.VerificadorDeTareasDeIngestaGoogleJwt;
import co.tecnosport.api.infrastructure.proveedores.whatsapp.ExportacionChatWhatsApp;
import com.google.cloud.storage.Storage;
import java.net.URI;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Mismo patrón que {@code ConfiguracionCatalogo}: un bean por caso de uso, los puertos ya
 * resueltos.
 */
@Configuration
@EnableConfigurationProperties({PropiedadesProveedores.class, PropiedadesColaDeIngestas.class})
public class ConfiguracionProveedores {

  private static final Logger log = LoggerFactory.getLogger(ConfiguracionProveedores.class);

  /**
   * Cada cuánto avisa el trabajador que sigue vivo con su lote, y cuánto puede pasar sin aviso
   * antes de darlo por abandonado. Parámetros técnicos: la tolerancia es seis latidos, para que una
   * pausa del recolector o una base lenta no maten una ingesta viva, y lo bastante corta para que
   * lo que dejó una instancia apagada se retome en minutos y no al arranque siguiente.
   */
  private static final Duration INTERVALO_DEL_LATIDO = Duration.ofSeconds(30);

  private static final Duration TOLERANCIA_DEL_LATIDO = Duration.ofMinutes(3);

  /**
   * Los tramos de Cloud Tasks (ADR-0080). La tarea espera la respuesta quince minutos, y el tramo
   * trabaja diez: la publicación que está en curso cuando se acaba el presupuesto termina primero,
   * y una extracción con reintentos y la lectura de un álbum de veintitrés fotos caben en los cinco
   * de holgura. Cloud Tasks admite hasta treinta; la API de Cloud Run tiene que esperar al menos lo
   * mismo (Terraform, {@code timeout}).
   */
  private static final Duration PLAZO_DE_LA_TAREA = Duration.ofMinutes(15);

  private static final Duration PRESUPUESTO_DEL_TRAMO = Duration.ofMinutes(10);

  /**
   * Cuánto puede esperar un lote en la cola sin que su tarea llegue antes de volver a encolarlo.
   * Más que el plazo de una tarea, para no duplicar la que todavía puede llegar.
   */
  private static final Duration TOLERANCIA_EN_LA_COLA = Duration.ofMinutes(20);

  /** Sobre el mismo {@code Storage} del catálogo: misma cuenta, otro bucket. */
  @Bean
  public AlmacenDeArchivosDeProveedor almacenDeArchivosDeProveedor(
      Storage storage, PropiedadesProveedores propiedades) {
    return new AlmacenDeArchivosDeProveedorGcs(
        storage, propiedades.bucket(), propiedades.minutosUrlFirmada());
  }

  @Bean
  public FuenteDeMensajes fuenteDeMensajes(
      AlmacenDeArchivosDeProveedor almacen, PropiedadesProveedores propiedades) {
    return new ExportacionChatWhatsApp(almacen, propiedades.descomprimidoMaximoBytes());
  }

  @Bean
  public CrearProveedor crearProveedor(RepositorioProveedores repositorio) {
    return new CrearProveedor(repositorio);
  }

  @Bean
  public EditarProveedor editarProveedor(RepositorioProveedores repositorio) {
    return new EditarProveedor(repositorio);
  }

  @Bean
  public EliminacionDeProductos eliminacionDeProductos(EliminarProducto eliminarProducto) {
    return new EliminacionDeProductosDelCatalogo(eliminarProducto);
  }

  @Bean
  public EliminarLoteDeIngesta eliminarLoteDeIngesta(
      RepositorioLotesIngesta repositorio,
      EliminacionDeProductos eliminacionDeProductos,
      AlmacenDeArchivosDeProveedor almacen) {
    return new EliminarLoteDeIngesta(repositorio, eliminacionDeProductos, almacen);
  }

  @Bean
  public PausarIngesta pausarIngesta(RepositorioLotesIngesta repositorio) {
    return new PausarIngesta(repositorio);
  }

  @Bean
  public ReanudarIngesta reanudarIngesta(RepositorioLotesIngesta repositorio) {
    return new ReanudarIngesta(repositorio);
  }

  @Bean
  public DetenerIngesta detenerIngesta(RepositorioLotesIngesta repositorio, Reloj reloj) {
    return new DetenerIngesta(repositorio, reloj);
  }

  @Bean
  public EliminarProveedor eliminarProveedor(
      RepositorioProveedores repositorio, AlmacenDeArchivosDeProveedor almacen) {
    return new EliminarProveedor(repositorio, almacen);
  }

  @Bean
  public SolicitarSubidaDeExportacion solicitarSubidaDeExportacion(
      RepositorioProveedores repositorio, AlmacenDeArchivosDeProveedor almacen) {
    return new SolicitarSubidaDeExportacion(repositorio, almacen);
  }

  @Bean
  public IniciarIngesta iniciarIngesta(
      RepositorioProveedores proveedores,
      RepositorioLotesIngesta lotes,
      RepositorioArchivosDeIngesta archivos,
      AlmacenDeArchivosDeProveedor almacen,
      Reloj reloj,
      PropiedadesProveedores propiedades) {
    return new IniciarIngesta(
        proveedores, lotes, archivos, almacen, reloj, propiedades.exportacionMaximaBytes());
  }

  @Bean
  public BorrarArchivoDeIngesta borrarArchivoDeIngesta(
      RepositorioArchivosDeIngesta archivos, AlmacenDeArchivosDeProveedor almacen, Reloj reloj) {
    return new BorrarArchivoDeIngesta(archivos, almacen, reloj);
  }

  @Bean
  public EncolarIngesta encolarIngesta(
      EjecutorDeIngestas ejecutor,
      RepositorioLotesIngesta lotes,
      EnTransaccionPropia enTransaccionPropia,
      Reloj reloj) {
    return new EncolarIngesta(ejecutor, lotes, enTransaccionPropia, reloj);
  }

  @Bean
  public RegistrarMensajesDeProveedor registrarMensajesDeProveedor(
      RepositorioProveedores proveedores,
      RepositorioLotesIngesta lotes,
      RepositorioMensajesProveedor mensajes,
      AlmacenDeArchivosDeProveedor almacen,
      CalculadorDePHash calculadorDePHash) {
    return new RegistrarMensajesDeProveedor(
        proveedores, lotes, mensajes, almacen, calculadorDePHash);
  }

  @Bean
  public ProcesarLoteDeIngesta procesarLoteDeIngesta(
      RepositorioLotesIngesta lotes,
      RepositorioProveedores proveedores,
      RepositorioMensajesProveedor mensajes,
      RepositorioPublicacionesProveedor publicaciones,
      FuenteDeMensajes fuente,
      RegistrarMensajesDeProveedor registrar,
      ArmarPublicaciones armar,
      ExtraerProductoDePublicacion extraer,
      ResolverBorrador resolver,
      EnTransaccionPropia enTransaccionPropia,
      Reloj reloj) {
    return new ProcesarLoteDeIngesta(
        lotes,
        proveedores,
        mensajes,
        publicaciones,
        fuente,
        registrar,
        armar,
        extraer,
        resolver,
        enTransaccionPropia,
        reloj,
        PRESUPUESTO_DEL_TRAMO,
        TOLERANCIA_DEL_LATIDO);
  }

  @Bean
  public RegistrarLatidoDeIngesta registrarLatidoDeIngesta(
      RepositorioLotesIngesta lotes, Reloj reloj) {
    return new RegistrarLatidoDeIngesta(lotes, reloj);
  }

  @Bean
  public MarcapasosConTemporizador marcapasosDeIngesta(
      RegistrarLatidoDeIngesta registrarLatido, EnTransaccionPropia enTransaccionPropia) {
    return new MarcapasosConTemporizador(
        loteId ->
            enTransaccionPropia.ejecutar(
                () -> {
                  registrarLatido.ejecutar(loteId);
                  return null;
                }),
        INTERVALO_DEL_LATIDO);
  }

  @Bean
  public AvanzarIngesta avanzarIngesta(
      ProcesarLoteDeIngesta procesar, EncolarIngesta encolar, MarcapasosDeIngesta marcapasos) {
    return new AvanzarIngesta(procesar::procesarTramo, encolar, marcapasos);
  }

  /**
   * Cloud Tasks si hay cola configurada; si no, el hilo con su cola en memoria. El del hilo llama a
   * {@link AvanzarIngesta}, que a su vez encola: se resuelve tarde, con el {@code ObjectProvider},
   * para no hacer un ciclo entre los dos.
   */
  @Bean
  public EjecutorDeIngestas ejecutorDeIngestas(
      PropiedadesColaDeIngestas cola,
      PropiedadesProveedores propiedades,
      ObjectProvider<AvanzarIngesta> avanzar) {
    if (cola.enCloudTasks()) {
      log.info("Ingesta de proveedores por Cloud Tasks, en la cola {}.", cola.cola());
      return new ColaDeIngestasCloudTasks(
          URI.create(cola.urlApi()),
          cola.cola(),
          cola.urlTramos(),
          cola.cuentaDeServicio(),
          cola.audiencia(),
          PLAZO_DE_LA_TAREA,
          TokenDeAccesoDeGoogle.conCredencialesPorOmision());
    }
    if (System.getenv("K_SERVICE") != null) {
      // En Cloud Run un hilo de fondo no tiene CPU entre peticiones: es el fallo del 11 de octubre
      // de 2026. Falta la configuración de la cola, no es una elección.
      log.warn(
          "Ingesta de proveedores en un hilo dentro de Cloud Run: sin CPU entre peticiones, los"
              + " lotes no van a avanzar. Falta tecnosport.proveedores.cola-de-tareas (ADR-0080).");
    } else {
      log.info("Ingesta de proveedores en un hilo, con la cola en memoria.");
    }
    return new EjecutorDeIngestasEnHilo(
        loteId -> avanzar.getObject().ejecutar(loteId), propiedades.colaDeIngestas());
  }

  /**
   * Sin cola de Cloud Tasks nadie llama al endpoint interno, y nadie tiene por qué poder: no acepta
   * ningún token.
   */
  @Bean
  public VerificadorDeTareasDeIngesta verificadorDeTareasDeIngesta(PropiedadesColaDeIngestas cola) {
    if (!cola.enCloudTasks()) {
      return token -> false;
    }
    return VerificadorDeTareasDeIngestaGoogleJwt.contraLlavesDeGoogle(
        cola.urlLlaves(), cola.audiencia(), cola.cuentaDeServicio());
  }

  @Bean
  public RecuperarIngestasAbandonadas recuperarIngestasAbandonadas(
      RepositorioLotesIngesta lotes, Reloj reloj) {
    return new RecuperarIngestasAbandonadas(
        lotes, reloj, TOLERANCIA_DEL_LATIDO, TOLERANCIA_EN_LA_COLA);
  }

  @Bean
  public ReanudarLotesDeIngesta reanudarLotesDeIngesta(
      RepositorioLotesIngesta lotes,
      RecuperarIngestasAbandonadas recuperar,
      EjecutorDeIngestas ejecutor) {
    return new ReanudarLotesDeIngesta(lotes, recuperar, ejecutor);
  }
}
