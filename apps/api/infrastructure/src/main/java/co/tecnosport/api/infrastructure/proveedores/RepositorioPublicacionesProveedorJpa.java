package co.tecnosport.api.infrastructure.proveedores;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.proveedores.RepositorioPublicacionesProveedor;
import co.tecnosport.api.domain.proveedores.EstadoPublicacionProveedor;
import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import co.tecnosport.api.infrastructure.proveedores.entidad.PublicacionMensajeJpaEntity;
import co.tecnosport.api.infrastructure.proveedores.entidad.PublicacionProveedorJpaEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioPublicacionesProveedorJpa implements RepositorioPublicacionesProveedor {

  private static final String TEXTO = "TEXTO";
  private static final String MEDIO = "MEDIO";

  private final PublicacionProveedorJpaRepository publicaciones;
  private final PublicacionMensajeJpaRepository mensajes;
  private final Reloj reloj;

  public RepositorioPublicacionesProveedorJpa(
      PublicacionProveedorJpaRepository publicaciones,
      PublicacionMensajeJpaRepository mensajes,
      Reloj reloj) {
    this.publicaciones = Objects.requireNonNull(publicaciones);
    this.mensajes = Objects.requireNonNull(mensajes);
    this.reloj = Objects.requireNonNull(reloj);
  }

  @Override
  public void guardarTodas(List<PublicacionProveedor> lista) {
    Instant ahora = reloj.ahora();
    List<PublicacionProveedorJpaEntity> filas = new ArrayList<>();
    List<PublicacionMensajeJpaEntity> composicion = new ArrayList<>();
    for (PublicacionProveedor publicacion : lista) {
      filas.add(aFila(publicacion, ahora));
      composicion.addAll(composicionDe(publicacion));
    }
    publicaciones.saveAll(filas);
    mensajes.saveAll(composicion);
  }

  /** La composición no cambia después de armada; solo el estado y el motivo. */
  @Override
  public void actualizar(PublicacionProveedor publicacion) {
    Instant creadoEn =
        publicaciones
            .findById(publicacion.id())
            .map(PublicacionProveedorJpaEntity::getCreadoEn)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No se actualiza una publicación que no existe: " + publicacion.id()));
    publicaciones.save(aFila(publicacion, creadoEn));
  }

  @Override
  public Optional<PublicacionProveedor> buscarPorId(UUID id) {
    return publicaciones.findById(id).map(fila -> aDominio(fila, composicionDe(List.of(fila))));
  }

  @Override
  public List<PublicacionProveedor> listarDeLote(UUID loteId) {
    List<PublicacionProveedorJpaEntity> filas =
        publicaciones.findByLoteIdOrderByFechaAscCreadoEnAsc(loteId);
    Map<UUID, List<PublicacionMensajeJpaEntity>> composicion = composicionDe(filas);
    return filas.stream().map(fila -> aDominio(fila, composicion)).toList();
  }

  private Map<UUID, List<PublicacionMensajeJpaEntity>> composicionDe(
      List<PublicacionProveedorJpaEntity> filas) {
    Map<UUID, List<PublicacionMensajeJpaEntity>> porPublicacion = new HashMap<>();
    if (filas.isEmpty()) {
      return porPublicacion;
    }
    List<UUID> ids = filas.stream().map(PublicacionProveedorJpaEntity::getId).toList();
    for (PublicacionMensajeJpaEntity fila :
        mensajes.findByClavePublicacionIdInOrderByClaveOrdenAsc(ids)) {
      porPublicacion
          .computeIfAbsent(fila.getClave().getPublicacionId(), k -> new ArrayList<>())
          .add(fila);
    }
    return porPublicacion;
  }

  private static PublicacionProveedorJpaEntity aFila(PublicacionProveedor p, Instant creadoEn) {
    return new PublicacionProveedorJpaEntity(
        p.id(),
        p.proveedorId(),
        p.loteId(),
        p.mensajePrincipalId(),
        p.fecha(),
        p.estado().name(),
        p.motivo().orElse(null),
        creadoEn);
  }

  private static List<PublicacionMensajeJpaEntity> composicionDe(PublicacionProveedor p) {
    List<PublicacionMensajeJpaEntity> filas = new ArrayList<>();
    List<UUID> textos = p.textosAdicionales();
    for (int i = 0; i < textos.size(); i++) {
      filas.add(new PublicacionMensajeJpaEntity(p.id(), TEXTO, i, textos.get(i)));
    }
    List<UUID> medios = p.medios();
    for (int i = 0; i < medios.size(); i++) {
      filas.add(new PublicacionMensajeJpaEntity(p.id(), MEDIO, i, medios.get(i)));
    }
    return filas;
  }

  private static PublicacionProveedor aDominio(
      PublicacionProveedorJpaEntity fila,
      Map<UUID, List<PublicacionMensajeJpaEntity>> composicion) {
    List<UUID> textos = new ArrayList<>();
    List<UUID> medios = new ArrayList<>();
    for (PublicacionMensajeJpaEntity parte : composicion.getOrDefault(fila.getId(), List.of())) {
      if (TEXTO.equals(parte.getClave().getRol())) {
        textos.add(parte.getMensajeId());
      } else {
        medios.add(parte.getMensajeId());
      }
    }
    return new PublicacionProveedor(
        fila.getId(),
        fila.getProveedorId(),
        fila.getLoteId(),
        fila.getMensajePrincipalId(),
        textos,
        medios,
        fila.getFecha(),
        EstadoPublicacionProveedor.valueOf(fila.getEstado()),
        fila.getMotivo());
  }
}
