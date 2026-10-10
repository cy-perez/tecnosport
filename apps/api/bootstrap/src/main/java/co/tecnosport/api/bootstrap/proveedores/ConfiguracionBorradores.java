package co.tecnosport.api.bootstrap.proveedores;

import co.tecnosport.api.application.catalogo.AgregarVariante;
import co.tecnosport.api.application.catalogo.AlmacenDeImagenes;
import co.tecnosport.api.application.catalogo.RepositorioAtributos;
import co.tecnosport.api.application.catalogo.RepositorioCategorias;
import co.tecnosport.api.application.catalogo.RepositorioMarcas;
import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.proveedores.AlmacenDeArchivosDeProveedor;
import co.tecnosport.api.application.proveedores.AprobarBorrador;
import co.tecnosport.api.application.proveedores.CalculadorDePHash;
import co.tecnosport.api.application.proveedores.ConfirmarFotoDeBorrador;
import co.tecnosport.api.application.proveedores.DescartarFotoDeBorrador;
import co.tecnosport.api.application.proveedores.EditarBorrador;
import co.tecnosport.api.application.proveedores.EliminarBorrador;
import co.tecnosport.api.application.proveedores.EliminarBorradoresSinAprobar;
import co.tecnosport.api.application.proveedores.ExpirarDisponibilidadDeProductos;
import co.tecnosport.api.application.proveedores.ProcesadorDeImagenes;
import co.tecnosport.api.application.proveedores.RechazarBorrador;
import co.tecnosport.api.application.proveedores.RepositorioBorradores;
import co.tecnosport.api.application.proveedores.RepositorioMensajesProveedor;
import co.tecnosport.api.application.proveedores.RepositorioProductosDeProveedor;
import co.tecnosport.api.application.proveedores.RepositorioProveedores;
import co.tecnosport.api.application.proveedores.RepositorioPublicacionesProveedor;
import co.tecnosport.api.application.proveedores.ResolverBorrador;
import co.tecnosport.api.application.proveedores.SolicitarSubidaDeFotoDeBorrador;
import co.tecnosport.api.application.proveedores.VerBorrador;
import co.tecnosport.api.infrastructure.proveedores.imagenes.CalculadorDePHashAwt;
import co.tecnosport.api.infrastructure.proveedores.imagenes.ProcesadorDeImagenesNulo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Los borradores: reconocer, aprobar y revisar. Aparte de la ingesta por tamaño, no por capa. */
@Configuration
public class ConfiguracionBorradores {

  @Bean
  public CalculadorDePHash calculadorDePHash() {
    return new CalculadorDePHashAwt();
  }

  /**
   * El nulo: sin recorte ni fondo en esta iteración. Se cambia aquí el día que exista el retoque.
   */
  @Bean
  public ProcesadorDeImagenes procesadorDeImagenes() {
    return new ProcesadorDeImagenesNulo();
  }

  @Bean
  public ResolverBorrador resolverBorrador(
      RepositorioBorradores borradores,
      RepositorioProductosDeProveedor productosDeProveedor,
      RepositorioProductos productos,
      RepositorioPublicacionesProveedor publicaciones,
      AlmacenDeArchivosDeProveedor almacen,
      CalculadorDePHash calculadorDePHash,
      Reloj reloj,
      PropiedadesProveedores propiedades) {
    return new ResolverBorrador(
        borradores,
        productosDeProveedor,
        productos,
        publicaciones,
        almacen,
        calculadorDePHash,
        reloj,
        propiedades.margenPorLinea(),
        propiedades.ganancia().aTopes(),
        propiedades.huella().umbralHamming());
  }

  @Bean
  public AprobarBorrador aprobarBorrador(
      RepositorioBorradores borradores,
      RepositorioPublicacionesProveedor publicaciones,
      RepositorioMensajesProveedor mensajes,
      RepositorioProveedores proveedores,
      RepositorioProductos productos,
      RepositorioProductosDeProveedor productosDeProveedor,
      RepositorioMarcas marcas,
      RepositorioCategorias categorias,
      RepositorioAtributos atributos,
      AgregarVariante agregarVariante,
      AlmacenDeArchivosDeProveedor almacenPrivado,
      AlmacenDeImagenes almacenDeImagenes,
      ProcesadorDeImagenes procesador,
      CalculadorDePHash calculadorDePHash,
      Reloj reloj) {
    return new AprobarBorrador(
        borradores,
        publicaciones,
        mensajes,
        proveedores,
        productos,
        productosDeProveedor,
        marcas,
        categorias,
        atributos,
        agregarVariante,
        almacenPrivado,
        almacenDeImagenes,
        procesador,
        calculadorDePHash,
        reloj);
  }

  @Bean
  public ExpirarDisponibilidadDeProductos expirarDisponibilidadDeProductos(
      RepositorioProductosDeProveedor productosDeProveedor,
      RepositorioProductos productos,
      RepositorioProveedores proveedores,
      Reloj reloj,
      PropiedadesProveedores propiedades) {
    return new ExpirarDisponibilidadDeProductos(
        productosDeProveedor,
        productos,
        proveedores,
        reloj,
        propiedades.ventanaDisponibilidad(),
        propiedades.tecnologia().ventanaDisponibilidad());
  }

  @Bean
  public EditarBorrador editarBorrador(RepositorioBorradores borradores) {
    return new EditarBorrador(borradores);
  }

  @Bean
  public RechazarBorrador rechazarBorrador(RepositorioBorradores borradores) {
    return new RechazarBorrador(borradores);
  }

  @Bean
  public DescartarFotoDeBorrador descartarFotoDeBorrador(
      RepositorioBorradores borradores,
      RepositorioPublicacionesProveedor publicaciones,
      RepositorioMensajesProveedor mensajes,
      AlmacenDeArchivosDeProveedor almacen) {
    return new DescartarFotoDeBorrador(borradores, publicaciones, mensajes, almacen);
  }

  @Bean
  public SolicitarSubidaDeFotoDeBorrador solicitarSubidaDeFotoDeBorrador(
      RepositorioBorradores borradores, AlmacenDeArchivosDeProveedor almacen) {
    return new SolicitarSubidaDeFotoDeBorrador(borradores, almacen);
  }

  @Bean
  public ConfirmarFotoDeBorrador confirmarFotoDeBorrador(
      RepositorioBorradores borradores,
      AlmacenDeArchivosDeProveedor almacen,
      ProcesadorDeImagenes procesador,
      Reloj reloj,
      PropiedadesProveedores propiedades) {
    return new ConfirmarFotoDeBorrador(
        borradores, almacen, procesador, reloj, propiedades.fotoMaximaBytes());
  }

  @Bean
  public EliminarBorrador eliminarBorrador(
      RepositorioBorradores borradores,
      RepositorioPublicacionesProveedor publicaciones,
      RepositorioMensajesProveedor mensajes,
      AlmacenDeArchivosDeProveedor almacen) {
    return new EliminarBorrador(borradores, publicaciones, mensajes, almacen);
  }

  @Bean
  public EliminarBorradoresSinAprobar eliminarBorradoresSinAprobar(
      RepositorioBorradores borradores, EliminarBorrador eliminarBorrador) {
    return new EliminarBorradoresSinAprobar(borradores, eliminarBorrador);
  }

  @Bean
  public VerBorrador verBorrador(
      RepositorioBorradores borradores,
      RepositorioPublicacionesProveedor publicaciones,
      RepositorioMensajesProveedor mensajes,
      AlmacenDeArchivosDeProveedor almacen) {
    return new VerBorrador(borradores, publicaciones, mensajes, almacen);
  }
}
