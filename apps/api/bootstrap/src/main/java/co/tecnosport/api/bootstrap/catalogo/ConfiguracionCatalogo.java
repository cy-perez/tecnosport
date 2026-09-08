package co.tecnosport.api.bootstrap.catalogo;

import co.tecnosport.api.application.catalogo.AbrirSetRotacion;
import co.tecnosport.api.application.catalogo.AgregarVariante;
import co.tecnosport.api.application.catalogo.AlmacenDeImagenes;
import co.tecnosport.api.application.catalogo.BuscarProductos;
import co.tecnosport.api.application.catalogo.CompletarSetRotacion;
import co.tecnosport.api.application.catalogo.ConfirmarImagenPrincipal;
import co.tecnosport.api.application.catalogo.CrearProducto;
import co.tecnosport.api.application.catalogo.EditarProducto;
import co.tecnosport.api.application.catalogo.EliminarSetRotacion;
import co.tecnosport.api.application.catalogo.ListarAtributos;
import co.tecnosport.api.application.catalogo.ListarCategorias;
import co.tecnosport.api.application.catalogo.ListarMapaDelSitio;
import co.tecnosport.api.application.catalogo.ListarMarcas;
import co.tecnosport.api.application.catalogo.ListarProductosAdmin;
import co.tecnosport.api.application.catalogo.PublicarSetRotacion;
import co.tecnosport.api.application.catalogo.RepositorioAtributos;
import co.tecnosport.api.application.catalogo.RepositorioCategorias;
import co.tecnosport.api.application.catalogo.RepositorioMapaDelSitio;
import co.tecnosport.api.application.catalogo.RepositorioMarcas;
import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.catalogo.RepositorioSetsRotacion;
import co.tecnosport.api.application.catalogo.SolicitarSubidaDeImagenPrincipal;
import co.tecnosport.api.application.catalogo.SolicitarSubidasDeRotacion;
import co.tecnosport.api.application.catalogo.VerFichaDeProducto;
import co.tecnosport.api.application.catalogo.VerProductoAdmin;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.infrastructure.catalogo.AlmacenDeImagenesGcs;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@code application} es framework-free a propósito: sus casos de uso no llevan {@code @Component}.
 * Aquí, en {@code bootstrap}, es donde se decide qué implementación de cada puerto usar y se
 * registran los casos de uso como beans — docs/01-arquitectura.md: "quién implementa el repositorio
 * lo decide bootstrap".
 */
@Configuration
@EnableConfigurationProperties(PropiedadesGcs.class)
public class ConfiguracionCatalogo {

  @Bean
  public Storage storage() {
    return StorageOptions.getDefaultInstance().getService();
  }

  @Bean
  public AlmacenDeImagenes almacenDeImagenes(Storage storage, PropiedadesGcs propiedades) {
    return new AlmacenDeImagenesGcs(
        storage,
        propiedades.bucketImagenes(),
        propiedades.urlPublica(),
        propiedades.minutosUrlFirmada());
  }

  @Bean
  public SolicitarSubidaDeImagenPrincipal solicitarSubidaDeImagenPrincipal(
      RepositorioProductos repositorioProductos, AlmacenDeImagenes almacenDeImagenes) {
    return new SolicitarSubidaDeImagenPrincipal(repositorioProductos, almacenDeImagenes);
  }

  @Bean
  public ConfirmarImagenPrincipal confirmarImagenPrincipal(
      RepositorioProductos repositorioProductos, AlmacenDeImagenes almacenDeImagenes) {
    return new ConfirmarImagenPrincipal(repositorioProductos, almacenDeImagenes);
  }

  @Bean
  public BuscarProductos buscarProductos(RepositorioProductos repositorioProductos) {
    return new BuscarProductos(repositorioProductos);
  }

  @Bean
  public VerFichaDeProducto verFichaDeProducto(RepositorioProductos repositorioProductos) {
    return new VerFichaDeProducto(repositorioProductos);
  }

  @Bean
  public ListarMapaDelSitio listarMapaDelSitio(RepositorioMapaDelSitio repositorioMapaDelSitio) {
    return new ListarMapaDelSitio(repositorioMapaDelSitio);
  }

  @Bean
  public ListarCategorias listarCategorias(RepositorioCategorias repositorioCategorias) {
    return new ListarCategorias(repositorioCategorias);
  }

  @Bean
  public ListarMarcas listarMarcas(RepositorioMarcas repositorioMarcas) {
    return new ListarMarcas(repositorioMarcas);
  }

  @Bean
  public ListarProductosAdmin listarProductosAdmin(RepositorioProductos repositorioProductos) {
    return new ListarProductosAdmin(repositorioProductos);
  }

  @Bean
  public CrearProducto crearProducto(
      RepositorioProductos repositorioProductos,
      RepositorioMarcas repositorioMarcas,
      RepositorioCategorias repositorioCategorias) {
    return new CrearProducto(repositorioProductos, repositorioMarcas, repositorioCategorias);
  }

  @Bean
  public VerProductoAdmin verProductoAdmin(RepositorioProductos repositorioProductos) {
    return new VerProductoAdmin(repositorioProductos);
  }

  @Bean
  public EditarProducto editarProducto(
      RepositorioProductos repositorioProductos,
      RepositorioMarcas repositorioMarcas,
      RepositorioCategorias repositorioCategorias) {
    return new EditarProducto(repositorioProductos, repositorioMarcas, repositorioCategorias);
  }

  @Bean
  public ListarAtributos listarAtributos(RepositorioAtributos repositorioAtributos) {
    return new ListarAtributos(repositorioAtributos);
  }

  @Bean
  public AgregarVariante agregarVariante(
      RepositorioProductos repositorioProductos,
      RepositorioAtributos repositorioAtributos,
      RepositorioInventario repositorioInventario,
      Reloj reloj) {
    return new AgregarVariante(
        repositorioProductos, repositorioAtributos, repositorioInventario, reloj);
  }

  @Bean
  public AbrirSetRotacion abrirSetRotacion(
      RepositorioProductos repositorioProductos,
      RepositorioSetsRotacion repositorioSetsRotacion,
      Reloj reloj) {
    return new AbrirSetRotacion(repositorioProductos, repositorioSetsRotacion, reloj);
  }

  @Bean
  public SolicitarSubidasDeRotacion solicitarSubidasDeRotacion(
      RepositorioSetsRotacion repositorioSetsRotacion, AlmacenDeImagenes almacenDeImagenes) {
    return new SolicitarSubidasDeRotacion(repositorioSetsRotacion, almacenDeImagenes);
  }

  @Bean
  public CompletarSetRotacion completarSetRotacion(
      RepositorioSetsRotacion repositorioSetsRotacion, AlmacenDeImagenes almacenDeImagenes) {
    return new CompletarSetRotacion(repositorioSetsRotacion, almacenDeImagenes);
  }

  @Bean
  public PublicarSetRotacion publicarSetRotacion(RepositorioSetsRotacion repositorioSetsRotacion) {
    return new PublicarSetRotacion(repositorioSetsRotacion);
  }

  @Bean
  public EliminarSetRotacion eliminarSetRotacion(
      RepositorioSetsRotacion repositorioSetsRotacion, AlmacenDeImagenes almacenDeImagenes) {
    return new EliminarSetRotacion(repositorioSetsRotacion, almacenDeImagenes);
  }
}
