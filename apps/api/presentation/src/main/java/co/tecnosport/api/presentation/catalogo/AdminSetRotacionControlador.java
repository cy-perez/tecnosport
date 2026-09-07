package co.tecnosport.api.presentation.catalogo;

import co.tecnosport.api.application.catalogo.AbrirSetRotacion;
import co.tecnosport.api.application.catalogo.AbrirSetRotacionComando;
import co.tecnosport.api.application.catalogo.CompletarSetRotacion;
import co.tecnosport.api.application.catalogo.CompletarSetRotacionComando;
import co.tecnosport.api.application.catalogo.EliminarSetRotacion;
import co.tecnosport.api.application.catalogo.FotogramaComando;
import co.tecnosport.api.application.catalogo.PublicarSetRotacion;
import co.tecnosport.api.application.catalogo.SolicitarSubidasDeRotacion;
import co.tecnosport.api.application.catalogo.SolicitarSubidasDeRotacionComando;
import co.tecnosport.api.application.catalogo.SubidaDeFotograma;
import co.tecnosport.api.domain.catalogo.SetRotacion;
import co.tecnosport.api.presentation.catalogo.dto.AbrirSetRotacionPeticion;
import co.tecnosport.api.presentation.catalogo.dto.CompletarSetRotacionPeticion;
import co.tecnosport.api.presentation.catalogo.dto.FotogramaPeticion;
import co.tecnosport.api.presentation.catalogo.dto.SetRotacionRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.SolicitarSubidasDeRotacionPeticion;
import co.tecnosport.api.presentation.catalogo.dto.SubidaDeFotogramaRespuesta;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Los cuatro pasos de un set de rotación —abrir, pedir las subidas, completar y publicar— más el
 * borrado. Bajo {@code /api/v1/admin/**}, protegido por rol ADMIN en {@code ConfiguracionSeguridad}
 * (bootstrap); este controlador no repite esa regla, mismo criterio que {@code
 * AdminProductoControlador}.
 *
 * <p>Completar escribe el set y sus N fotogramas, así que la transacción se abre aquí: un set
 * marcado COMPLETO al que le faltan filas de imagen es un visor roto. El actor de auditoría sale
 * directo de {@code SecurityContextHolder}, por el motivo que explica {@code
 * AdminPedidosControlador}.
 */
@RestController
@RequestMapping("/api/v1/admin/sets-rotacion")
public class AdminSetRotacionControlador {

  private static final Logger log = LoggerFactory.getLogger(AdminSetRotacionControlador.class);

  private final AbrirSetRotacion abrirSetRotacion;
  private final SolicitarSubidasDeRotacion solicitarSubidasDeRotacion;
  private final CompletarSetRotacion completarSetRotacion;
  private final PublicarSetRotacion publicarSetRotacion;
  private final EliminarSetRotacion eliminarSetRotacion;
  private final MapeadorRespuestasSetRotacion mapeador;
  private final TransactionTemplate transaccion;

  public AdminSetRotacionControlador(
      AbrirSetRotacion abrirSetRotacion,
      SolicitarSubidasDeRotacion solicitarSubidasDeRotacion,
      CompletarSetRotacion completarSetRotacion,
      PublicarSetRotacion publicarSetRotacion,
      EliminarSetRotacion eliminarSetRotacion,
      MapeadorRespuestasSetRotacion mapeador,
      PlatformTransactionManager transactionManager) {
    this.abrirSetRotacion = Objects.requireNonNull(abrirSetRotacion);
    this.solicitarSubidasDeRotacion = Objects.requireNonNull(solicitarSubidasDeRotacion);
    this.completarSetRotacion = Objects.requireNonNull(completarSetRotacion);
    this.publicarSetRotacion = Objects.requireNonNull(publicarSetRotacion);
    this.eliminarSetRotacion = Objects.requireNonNull(eliminarSetRotacion);
    this.mapeador = Objects.requireNonNull(mapeador);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public SetRotacionRespuesta abrir(@RequestBody AbrirSetRotacionPeticion cuerpo) {
    SetRotacion set =
        abrirSetRotacion.ejecutar(
            new AbrirSetRotacionComando(
                cuerpo.productoId(),
                cuerpo.fotogramas(),
                "admin:" + actorId(),
                cuerpo.dispositivo(),
                cuerpo.versionAsistente()));
    return mapeador.aRespuesta(set);
  }

  @PostMapping("/{id}/subidas")
  @ResponseStatus(HttpStatus.CREATED)
  public List<SubidaDeFotogramaRespuesta> subidas(
      @PathVariable("id") UUID id, @RequestBody SolicitarSubidasDeRotacionPeticion cuerpo) {
    List<SubidaDeFotograma> subidas =
        solicitarSubidasDeRotacion.ejecutar(
            new SolicitarSubidasDeRotacionComando(id, cuerpo.contentType()));
    return subidas.stream()
        .map(s -> new SubidaDeFotogramaRespuesta(s.orden(), s.url(), s.objectKey()))
        .toList();
  }

  @PostMapping("/{id}/completar")
  public SetRotacionRespuesta completar(
      @PathVariable("id") UUID id, @RequestBody CompletarSetRotacionPeticion cuerpo) {
    List<FotogramaComando> fotogramas =
        cuerpo.fotogramas() == null
            ? List.of()
            : cuerpo.fotogramas().stream().map(AdminSetRotacionControlador::aComando).toList();
    SetRotacion set =
        transaccion.execute(
            estado ->
                completarSetRotacion.ejecutar(new CompletarSetRotacionComando(id, fotogramas)));
    return mapeador.aRespuesta(set);
  }

  @PostMapping("/{id}/publicar")
  public SetRotacionRespuesta publicar(@PathVariable("id") UUID id) {
    return mapeador.aRespuesta(publicarSetRotacion.ejecutar(id));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void eliminar(@PathVariable("id") UUID id) {
    int objetosBorrados = eliminarSetRotacion.ejecutar(id);
    log.info("Set de rotación {} eliminado; {} objetos borrados del bucket.", id, objetosBorrados);
  }

  private static FotogramaComando aComando(FotogramaPeticion peticion) {
    return new FotogramaComando(
        peticion.orden(), peticion.objectKey(), peticion.ancho(), peticion.alto(), peticion.hash());
  }

  private UUID actorId() {
    return (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
  }
}
