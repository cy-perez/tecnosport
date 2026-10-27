package co.tecnosport.api.bootstrap.proveedores;

import co.tecnosport.api.application.catalogo.AgregarVariante;
import co.tecnosport.api.application.catalogo.AlmacenDeImagenes;
import co.tecnosport.api.application.catalogo.RepositorioAtributos;
import co.tecnosport.api.application.catalogo.RepositorioCategorias;
import co.tecnosport.api.application.catalogo.RepositorioMarcas;
import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.compartido.EnTransaccionPropia;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.application.proveedores.AlmacenDeArchivosDeProveedor;
import co.tecnosport.api.application.proveedores.RepositorioProductosDeProveedor;
import co.tecnosport.api.application.proveedores.RepositorioProveedores;
import co.tecnosport.api.application.proveedores.tecnologia.AgregarFotoABorradorTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.AprobarBorradorTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.DefinirPaletaDeTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.EditarBorradorTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.EditarFichaBorradorTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.EliminarBorradorTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.EliminarBorradoresTecnologiaSinAprobar;
import co.tecnosport.api.application.proveedores.tecnologia.FotosFirmadasDeBorradorTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.ImportarListaDeTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.ListarBorradoresTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.QuitarFotoDeBorradorTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.RechazarBorradorTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.ReordenarFotosBorradorTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.RepositorioBorradoresTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.RepositorioListasDeTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.RepositorioVariantesDeProveedor;
import co.tecnosport.api.application.proveedores.tecnologia.SolicitarSubidaDeFotoDeBorradorTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.VerBorradorTecnologia;
import co.tecnosport.api.domain.proveedores.SugerenciaDePrecioTecnologia;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** La tecnología por listas de precios (ADR-0075). */
@Configuration
public class ConfiguracionListasDeTecnologia {

  /** El precio de contado sugerido de cada configuración, con su tope (ADR-0095, decisión 8). */
  @Bean
  public SugerenciaDePrecioTecnologia sugerenciaDePrecioTecnologia(
      PropiedadesProveedores propiedades) {
    return propiedades.tecnologia().aSugerencia();
  }

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
      RepositorioListasDeTecnologia listas,
      RepositorioMarcas marcas,
      RepositorioCategorias categorias,
      RepositorioAtributos atributos,
      AgregarVariante agregarVariante,
      AlmacenDeArchivosDeProveedor almacenPrivado,
      AlmacenDeImagenes almacenDeImagenes,
      EnTransaccionPropia enTransaccionPropia,
      PropiedadesProveedores propiedades) {
    return new AprobarBorradorTecnologia(
        borradores,
        variantes,
        productos,
        productosDeProveedor,
        listas,
        marcas,
        categorias,
        atributos,
        agregarVariante,
        almacenPrivado,
        almacenDeImagenes,
        enTransaccionPropia,
        propiedades.tecnologia().existenciaPorVariante());
  }

  @Bean
  public DefinirPaletaDeTecnologia definirPaletaDeTecnologia(
      RepositorioProductos productos,
      RepositorioProductosDeProveedor productosDeProveedor,
      RepositorioVariantesDeProveedor variantes,
      AgregarVariante agregarVariante) {
    return new DefinirPaletaDeTecnologia(
        productos, productosDeProveedor, variantes, agregarVariante);
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
  public EditarFichaBorradorTecnologia editarFichaBorradorTecnologia(
      RepositorioBorradoresTecnologia borradores) {
    return new EditarFichaBorradorTecnologia(borradores);
  }

  @Bean
  public EliminarBorradorTecnologia eliminarBorradorTecnologia(
      RepositorioBorradoresTecnologia borradores,
      AlmacenDeArchivosDeProveedor almacen,
      EnTransaccionPropia enTransaccionPropia) {
    return new EliminarBorradorTecnologia(borradores, almacen, enTransaccionPropia);
  }

  @Bean
  public EliminarBorradoresTecnologiaSinAprobar eliminarBorradoresTecnologiaSinAprobar(
      RepositorioBorradoresTecnologia borradores, EliminarBorradorTecnologia eliminarBorrador) {
    return new EliminarBorradoresTecnologiaSinAprobar(borradores, eliminarBorrador);
  }

  @Bean
  public ReordenarFotosBorradorTecnologia reordenarFotosBorradorTecnologia(
      RepositorioBorradoresTecnologia borradores) {
    return new ReordenarFotosBorradorTecnologia(borradores);
  }

  @Bean
  public RechazarBorradorTecnologia rechazarBorradorTecnologia(
      RepositorioBorradoresTecnologia borradores) {
    return new RechazarBorradorTecnologia(borradores);
  }

  @Bean
  public SolicitarSubidaDeFotoDeBorradorTecnologia solicitarSubidaDeFotoDeBorradorTecnologia(
      RepositorioBorradoresTecnologia borradores, AlmacenDeArchivosDeProveedor almacen) {
    return new SolicitarSubidaDeFotoDeBorradorTecnologia(borradores, almacen);
  }

  @Bean
  public AgregarFotoABorradorTecnologia agregarFotoABorradorTecnologia(
      RepositorioBorradoresTecnologia borradores,
      AlmacenDeArchivosDeProveedor almacen,
      Reloj reloj) {
    return new AgregarFotoABorradorTecnologia(borradores, almacen, reloj);
  }

  @Bean
  public QuitarFotoDeBorradorTecnologia quitarFotoDeBorradorTecnologia(
      RepositorioBorradoresTecnologia borradores, AlmacenDeArchivosDeProveedor almacen) {
    return new QuitarFotoDeBorradorTecnologia(borradores, almacen);
  }

  @Bean
  public FotosFirmadasDeBorradorTecnologia fotosFirmadasDeBorradorTecnologia(
      AlmacenDeArchivosDeProveedor almacen) {
    return new FotosFirmadasDeBorradorTecnologia(almacen);
  }
}
