package co.tecnosport.api.bootstrap.proveedores;

import co.tecnosport.api.application.catalogo.EliminarProducto;
import co.tecnosport.api.application.compartido.EnTransaccionPropia;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.proveedores.AlmacenDeArchivosDeProveedor;
import co.tecnosport.api.application.proveedores.ArmarPublicaciones;
import co.tecnosport.api.application.proveedores.CrearProveedor;
import co.tecnosport.api.application.proveedores.EditarProveedor;
import co.tecnosport.api.application.proveedores.EjecutorDeIngestas;
import co.tecnosport.api.application.proveedores.EliminacionDeProductos;
import co.tecnosport.api.application.proveedores.EliminacionDeProductosDelCatalogo;
import co.tecnosport.api.application.proveedores.EliminarLoteDeIngesta;
import co.tecnosport.api.application.proveedores.EliminarProveedor;
import co.tecnosport.api.application.proveedores.ExtraerProductoDePublicacion;
import co.tecnosport.api.application.proveedores.FuenteDeMensajes;
import co.tecnosport.api.application.proveedores.IniciarIngesta;
import co.tecnosport.api.application.proveedores.ProcesarLoteDeIngesta;
import co.tecnosport.api.application.proveedores.ReanudarLotesDeIngesta;
import co.tecnosport.api.application.proveedores.RegistrarMensajesDeProveedor;
import co.tecnosport.api.application.proveedores.RepositorioLotesIngesta;
import co.tecnosport.api.application.proveedores.RepositorioMensajesProveedor;
import co.tecnosport.api.application.proveedores.RepositorioProveedores;
import co.tecnosport.api.application.proveedores.RepositorioPublicacionesProveedor;
import co.tecnosport.api.application.proveedores.ResolverBorrador;
import co.tecnosport.api.application.proveedores.SolicitarSubidaDeExportacion;
import co.tecnosport.api.infrastructure.proveedores.AlmacenDeArchivosDeProveedorGcs;
import co.tecnosport.api.infrastructure.proveedores.whatsapp.ExportacionChatWhatsApp;
import com.google.cloud.storage.Storage;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Mismo patrón que {@code ConfiguracionCatalogo}: un bean por caso de uso, los puertos ya
 * resueltos.
 */
@Configuration
@EnableConfigurationProperties(PropiedadesProveedores.class)
public class ConfiguracionProveedores {

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
      AlmacenDeArchivosDeProveedor almacen,
      Reloj reloj,
      PropiedadesProveedores propiedades) {
    return new IniciarIngesta(
        proveedores, lotes, almacen, reloj, propiedades.exportacionMaximaBytes());
  }

  @Bean
  public RegistrarMensajesDeProveedor registrarMensajesDeProveedor(
      RepositorioProveedores proveedores,
      RepositorioLotesIngesta lotes,
      RepositorioMensajesProveedor mensajes,
      AlmacenDeArchivosDeProveedor almacen) {
    return new RegistrarMensajesDeProveedor(proveedores, lotes, mensajes, almacen);
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
        reloj);
  }

  @Bean
  public EjecutorDeIngestas ejecutorDeIngestas(
      ProcesarLoteDeIngesta procesar, PropiedadesProveedores propiedades) {
    return new EjecutorDeIngestasEnHilo(procesar, propiedades.colaDeIngestas());
  }

  @Bean
  public ReanudarLotesDeIngesta reanudarLotesDeIngesta(
      RepositorioLotesIngesta lotes, EjecutorDeIngestas ejecutor, Reloj reloj) {
    return new ReanudarLotesDeIngesta(lotes, ejecutor, reloj);
  }
}
