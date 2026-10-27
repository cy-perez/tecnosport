package co.tecnosport.api.presentation.proveedores;

import co.tecnosport.api.application.catalogo.SolicitudDeSubida;
import co.tecnosport.api.application.proveedores.tecnologia.AgregarFotoABorradorTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.AprobarBorradorTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.AprobarBorradorTecnologiaComando;
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
import co.tecnosport.api.application.proveedores.tecnologia.SolicitarSubidaDeFotoDeBorradorTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.VerBorradorTecnologia;
import co.tecnosport.api.domain.catalogo.ComisionDePasarela;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.proveedores.BorradorTecnologia;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import co.tecnosport.api.domain.proveedores.SugerenciaDePrecioTecnologia;
import co.tecnosport.api.presentation.catalogo.MapeadorRespuestasProductoAdmin;
import co.tecnosport.api.presentation.catalogo.dto.ProductoAdminRespuesta;
import co.tecnosport.api.presentation.proveedores.dto.AgregarFotoBorradorTecnologiaPeticion;
import co.tecnosport.api.presentation.proveedores.dto.AprobarBorradorTecnologiaPeticion;
import co.tecnosport.api.presentation.proveedores.dto.BorradorTecnologiaRespuesta;
import co.tecnosport.api.presentation.proveedores.dto.BorradoresEliminadosRespuesta;
import co.tecnosport.api.presentation.proveedores.dto.BorradoresSinAprobarRespuesta;
import co.tecnosport.api.presentation.proveedores.dto.EditarFichaBorradorTecnologiaPeticion;
import co.tecnosport.api.presentation.proveedores.dto.ElegirConfiguracionesPeticion;
import co.tecnosport.api.presentation.proveedores.dto.ImportacionTecnologiaRespuesta;
import co.tecnosport.api.presentation.proveedores.dto.ImportarListaTecnologiaPeticion;
import co.tecnosport.api.presentation.proveedores.dto.OrdenDeFotosBorradorTecnologiaPeticion;
import co.tecnosport.api.presentation.proveedores.dto.RechazarBorradorPeticion;
import co.tecnosport.api.presentation.proveedores.dto.SolicitarSubidaDeFotoPeticion;
import co.tecnosport.api.presentation.proveedores.dto.SubidaDeFotoRespuesta;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * La tecnología por listas (ADR-0075): importar la lista del día que exporta la skill, y la bandeja
 * de borradores de tecnología —ver, elegir colores y precios, aprobar y rechazar—.
 *
 * <p>Importar corre en una sola transacción: la lista entra entera o no entra. Toca muchos libros
 * de inventario con bloqueo, pero en orden, y la lista se importa a mano de vez en cuando.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminTecnologiaControlador {

  private static final Logger log = LoggerFactory.getLogger(AdminTecnologiaControlador.class);

  /**
   * Borradores por tanda del borrado en bloque. Diez y no los cien de las prendas: cada uno lleva
   * unas quince piezas en el bucket —cada foto con sus anchos y su vista previa—.
   */
  static final int TANDA_DE_BORRADO = 10;

  private final ImportarListaDeTecnologia importarLista;
  private final ListarBorradoresTecnologia listarBorradores;
  private final VerBorradorTecnologia verBorrador;
  private final EditarBorradorTecnologia editarBorrador;
  private final AprobarBorradorTecnologia aprobarBorrador;
  private final RechazarBorradorTecnologia rechazarBorrador;
  private final SolicitarSubidaDeFotoDeBorradorTecnologia solicitarSubidaDeFoto;
  private final AgregarFotoABorradorTecnologia agregarFoto;
  private final QuitarFotoDeBorradorTecnologia quitarFoto;
  private final ReordenarFotosBorradorTecnologia reordenarFotos;
  private final EditarFichaBorradorTecnologia editarFicha;
  private final EliminarBorradorTecnologia eliminarBorrador;
  private final EliminarBorradoresTecnologiaSinAprobar eliminarSinAprobar;
  private final FotosFirmadasDeBorradorTecnologia fotosFirmadas;
  private final MapeadorRespuestasProductoAdmin mapeadorProducto;
  private final TransactionTemplate transaccion;

  /** Los dos precios sugeridos de cada configuración (ADR-0095, decisión 8). */
  private final BorradorTecnologiaRespuesta.Precios precios;

  public AdminTecnologiaControlador(
      ImportarListaDeTecnologia importarLista,
      ListarBorradoresTecnologia listarBorradores,
      VerBorradorTecnologia verBorrador,
      EditarBorradorTecnologia editarBorrador,
      AprobarBorradorTecnologia aprobarBorrador,
      RechazarBorradorTecnologia rechazarBorrador,
      SolicitarSubidaDeFotoDeBorradorTecnologia solicitarSubidaDeFoto,
      AgregarFotoABorradorTecnologia agregarFoto,
      QuitarFotoDeBorradorTecnologia quitarFoto,
      ReordenarFotosBorradorTecnologia reordenarFotos,
      EditarFichaBorradorTecnologia editarFicha,
      EliminarBorradorTecnologia eliminarBorrador,
      EliminarBorradoresTecnologiaSinAprobar eliminarSinAprobar,
      FotosFirmadasDeBorradorTecnologia fotosFirmadas,
      MapeadorRespuestasProductoAdmin mapeadorProducto,
      PlatformTransactionManager transactionManager,
      SugerenciaDePrecioTecnologia sugerenciaDePrecio,
      ComisionDePasarela comision) {
    this.importarLista = Objects.requireNonNull(importarLista);
    this.listarBorradores = Objects.requireNonNull(listarBorradores);
    this.verBorrador = Objects.requireNonNull(verBorrador);
    this.editarBorrador = Objects.requireNonNull(editarBorrador);
    this.aprobarBorrador = Objects.requireNonNull(aprobarBorrador);
    this.rechazarBorrador = Objects.requireNonNull(rechazarBorrador);
    this.solicitarSubidaDeFoto = Objects.requireNonNull(solicitarSubidaDeFoto);
    this.agregarFoto = Objects.requireNonNull(agregarFoto);
    this.quitarFoto = Objects.requireNonNull(quitarFoto);
    this.reordenarFotos = Objects.requireNonNull(reordenarFotos);
    this.editarFicha = Objects.requireNonNull(editarFicha);
    this.eliminarBorrador = Objects.requireNonNull(eliminarBorrador);
    this.eliminarSinAprobar = Objects.requireNonNull(eliminarSinAprobar);
    this.fotosFirmadas = Objects.requireNonNull(fotosFirmadas);
    this.mapeadorProducto = Objects.requireNonNull(mapeadorProducto);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
    this.precios =
        new BorradorTecnologiaRespuesta.Precios(
            Objects.requireNonNull(sugerenciaDePrecio), Objects.requireNonNull(comision));
  }

  @PostMapping("/proveedores/{proveedorId}/listas-tecnologia")
  public ImportacionTecnologiaRespuesta importar(
      @PathVariable UUID proveedorId, @RequestBody ImportarListaTecnologiaPeticion cuerpo) {
    ImportarListaDeTecnologia.Resultado resultado =
        transaccion.execute(estado -> importarLista.ejecutar(cuerpo.aComando(proveedorId)));
    log.info(
        "Lista de tecnología del {} importada para el proveedor {}: {} renovados, {} borradores"
            + " nuevos, {} variantes retiradas, {} modelos agotados",
        cuerpo.fechaLista(),
        proveedorId,
        resultado.productosRenovados(),
        resultado.borradoresNuevos(),
        resultado.variantesRetiradas(),
        resultado.modelosAgotados());
    return ImportacionTecnologiaRespuesta.de(resultado);
  }

  @GetMapping("/borradores-tecnologia")
  public List<BorradorTecnologiaRespuesta> listar(
      @RequestParam(defaultValue = "EN_REVISION") String estado) {
    return listarBorradores.ejecutar(EstadoBorrador.valueOf(estado)).stream()
        .map(b -> BorradorTecnologiaRespuesta.de(b, precios))
        .toList();
  }

  /** Cuántos borraría el borrado en bloque —en revisión y rechazados—: el panel lo dice antes. */
  @GetMapping("/borradores-tecnologia/sin-aprobar")
  public BorradoresSinAprobarRespuesta contarSinAprobar() {
    return new BorradoresSinAprobarRespuesta(eliminarSinAprobar.contar());
  }

  /**
   * Una tanda del borrado en bloque de los borradores de tecnología sin aprobar, con sus fotos.
   * Responde cuántos quedan; el panel repite mientras no sea cero. Los aprobados se quedan.
   */
  @DeleteMapping("/borradores-tecnologia/sin-aprobar")
  public BorradoresEliminadosRespuesta eliminarSinAprobar() {
    // Sin transacción aquí: cada borrador abre la suya, y el bucket se borra al confirmarla.
    var resultado = eliminarSinAprobar.ejecutar(TANDA_DE_BORRADO);
    log.warn(
        "Borradores de tecnología sin aprobar eliminados: {} ({} archivos borrados del bucket,"
            + " quedan {})",
        resultado.eliminados(),
        resultado.archivosBorrados(),
        resultado.quedan());
    return BorradoresEliminadosRespuesta.de(resultado);
  }

  /** Borra un borrador en revisión o rechazado con sus fotos; uno aprobado responde 409. */
  @DeleteMapping("/borradores-tecnologia/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void eliminar(@PathVariable UUID id) {
    // Sin transacción aquí: el caso de uso abre la suya y borra el bucket al confirmarla.
    int objetos = eliminarBorrador.ejecutar(id);
    log.warn("Borrador de tecnología eliminado: {} ({} archivos borrados del bucket)", id, objetos);
  }

  @GetMapping("/borradores-tecnologia/{id}")
  public BorradorTecnologiaRespuesta ver(@PathVariable UUID id) {
    return conFotos(verBorrador.ejecutar(id));
  }

  @PatchMapping("/borradores-tecnologia/{id}")
  public BorradorTecnologiaRespuesta elegir(
      @PathVariable UUID id, @RequestBody ElegirConfiguracionesPeticion cuerpo) {
    return conFotos(transaccion.execute(estado -> editarBorrador.ejecutar(id, cuerpo.aDominio())));
  }

  @PostMapping("/borradores-tecnologia/{id}/aprobar")
  public ProductoAdminRespuesta aprobar(
      @PathVariable UUID id, @RequestBody AprobarBorradorTecnologiaPeticion cuerpo) {
    // Sin transacción aquí: el caso de uso abre la suya y limpia el bucket privado al confirmarla.
    Producto producto =
        aprobarBorrador.ejecutar(
            new AprobarBorradorTecnologiaComando(id, cuerpo.marcaId(), cuerpo.categoriaId()));
    log.info(
        "Borrador de tecnología {} aprobado: producto {} con {} variante(s)",
        id,
        producto.id(),
        producto.variantes().size());
    return mapeadorProducto.aRespuesta(producto);
  }

  @PostMapping("/borradores-tecnologia/{id}/rechazar")
  public BorradorTecnologiaRespuesta rechazar(
      @PathVariable UUID id, @RequestBody RechazarBorradorPeticion cuerpo) {
    return conFotos(transaccion.execute(estado -> rechazarBorrador.ejecutar(id, cuerpo.motivo())));
  }

  /**
   * El primer paso para subir un archivo de una foto —un ancho o la vista previa— al borrador: la
   * URL firmada para subirlo directo al bucket privado del proveedor (ADR-0079).
   */
  @PostMapping("/borradores-tecnologia/{id}/fotos/url-subida")
  public SubidaDeFotoRespuesta urlDeSubidaDeFoto(
      @PathVariable UUID id, @RequestBody SolicitarSubidaDeFotoPeticion cuerpo) {
    SolicitudDeSubida solicitud = solicitarSubidaDeFoto.ejecutar(id, cuerpo.contentType());
    return new SubidaDeFotoRespuesta(solicitud.url(), solicitud.objectKey());
  }

  /**
   * El segundo: el servidor comprueba los archivos de la foto y la cuelga al final del borrador.
   */
  @PostMapping("/borradores-tecnologia/{id}/fotos")
  @ResponseStatus(HttpStatus.CREATED)
  public BorradorTecnologiaRespuesta agregarFoto(
      @PathVariable UUID id, @RequestBody AgregarFotoBorradorTecnologiaPeticion cuerpo) {
    transaccion.executeWithoutResult(estado -> agregarFoto.ejecutar(cuerpo.aComando(id)));
    return conFotos(verBorrador.ejecutar(id));
  }

  /** Quita la foto del borrador y borra sus archivos. */
  @DeleteMapping("/borradores-tecnologia/{id}/fotos/{fotoId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void quitarFoto(@PathVariable UUID id, @PathVariable UUID fotoId) {
    transaccion.executeWithoutResult(estado -> quitarFoto.ejecutar(id, fotoId));
  }

  /** Cambia el orden de las fotos: la primera es la principal del producto al aprobar. */
  @PutMapping("/borradores-tecnologia/{id}/fotos/orden")
  public BorradorTecnologiaRespuesta reordenarFotos(
      @PathVariable UUID id, @RequestBody OrdenDeFotosBorradorTecnologiaPeticion cuerpo) {
    return conFotos(transaccion.execute(estado -> reordenarFotos.ejecutar(id, cuerpo.fotoIds())));
  }

  /**
   * La ficha que se publicará al aprobar, editada a mano. En blanco vuelve a la que preparó la
   * skill.
   */
  @PutMapping("/borradores-tecnologia/{id}/descripcion")
  public BorradorTecnologiaRespuesta editarFicha(
      @PathVariable UUID id, @RequestBody EditarFichaBorradorTecnologiaPeticion cuerpo) {
    return conFotos(transaccion.execute(estado -> editarFicha.ejecutar(id, cuerpo.descripcion())));
  }

  private BorradorTecnologiaRespuesta conFotos(BorradorTecnologia borrador) {
    return BorradorTecnologiaRespuesta.de(borrador, fotosFirmadas.ejecutar(borrador), precios);
  }
}
