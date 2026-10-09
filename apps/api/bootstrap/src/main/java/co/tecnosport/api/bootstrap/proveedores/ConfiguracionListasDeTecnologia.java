package co.tecnosport.api.bootstrap.proveedores;

import co.tecnosport.api.application.catalogo.AgregarVariante;
import co.tecnosport.api.application.catalogo.RepositorioAtributos;
import co.tecnosport.api.application.catalogo.RepositorioCategorias;
import co.tecnosport.api.application.catalogo.RepositorioMarcas;
import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.application.proveedores.RepositorioProductosDeProveedor;
import co.tecnosport.api.application.proveedores.RepositorioProveedores;
import co.tecnosport.api.application.proveedores.tecnologia.AprobarBorradorTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.EditarBorradorTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.ImportarListaDeTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.ListarBorradoresTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.RechazarBorradorTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.RepositorioBorradoresTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.RepositorioListasDeTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.RepositorioVariantesDeProveedor;
import co.tecnosport.api.application.proveedores.tecnologia.VerBorradorTecnologia;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** La tecnología por listas de precios (ADR-0075). */
@Configuration
public class ConfiguracionListasDeTecnologia {

  @Bean
  public ImportarListaDeTecnologia importarListaDeTecnologia(
      RepositorioProveedores proveedores,
      RepositorioProductosDeProveedor productosDeProveedor,
      RepositorioProductos productos,
      RepositorioBorradoresTecnologia borradores,
      RepositorioVariantesDeProveedor variantes,
      RepositorioInventario inventario,
      RepositorioListasDeTecnologia listas,
      Reloj reloj,
      PropiedadesProveedores propiedades) {
    return new ImportarListaDeTecnologia(
        proveedores,
        productosDeProveedor,
        productos,
        borradores,
        variantes,
        inventario,
        listas,
        reloj,
        propiedades.tecnologia().existenciaPorVariante());
  }

  @Bean
  public AprobarBorradorTecnologia aprobarBorradorTecnologia(
      RepositorioBorradoresTecnologia borradores,
      RepositorioVariantesDeProveedor variantes,
      RepositorioProductos productos,
      RepositorioProductosDeProveedor productosDeProveedor,
      RepositorioMarcas marcas,
      RepositorioCategorias categorias,
      RepositorioAtributos atributos,
      AgregarVariante agregarVariante,
      PropiedadesProveedores propiedades) {
    return new AprobarBorradorTecnologia(
        borradores,
        variantes,
        productos,
        productosDeProveedor,
        marcas,
        categorias,
        atributos,
        agregarVariante,
        propiedades.tecnologia().existenciaPorVariante());
  }

  @Bean
  public ListarBorradoresTecnologia listarBorradoresTecnologia(
      RepositorioBorradoresTecnologia borradores) {
    return new ListarBorradoresTecnologia(borradores);
  }

  @Bean
  public VerBorradorTecnologia verBorradorTecnologia(RepositorioBorradoresTecnologia borradores) {
    return new VerBorradorTecnologia(borradores);
  }

  @Bean
  public EditarBorradorTecnologia editarBorradorTecnologia(
      RepositorioBorradoresTecnologia borradores) {
    return new EditarBorradorTecnologia(borradores);
  }

  @Bean
  public RechazarBorradorTecnologia rechazarBorradorTecnologia(
      RepositorioBorradoresTecnologia borradores) {
    return new RechazarBorradorTecnologia(borradores);
  }
}
