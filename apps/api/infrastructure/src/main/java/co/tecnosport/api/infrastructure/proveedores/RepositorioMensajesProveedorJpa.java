package co.tecnosport.api.infrastructure.proveedores;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.proveedores.RepositorioMensajesProveedor;
import co.tecnosport.api.domain.proveedores.IdExternoDeMensaje;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import co.tecnosport.api.domain.proveedores.PHash;
import co.tecnosport.api.domain.proveedores.TipoMensaje;
import co.tecnosport.api.infrastructure.proveedores.entidad.MensajeProveedorJpaEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioMensajesProveedorJpa implements RepositorioMensajesProveedor {

  private final MensajeProveedorJpaRepository jpa;
  private final Reloj reloj;

  public RepositorioMensajesProveedorJpa(MensajeProveedorJpaRepository jpa, Reloj reloj) {
    this.jpa = Objects.requireNonNull(jpa);
    this.reloj = Objects.requireNonNull(reloj);
  }

  @Override
  public void guardarTodos(List<MensajeProveedor> mensajes) {
    Instant ahora = reloj.ahora();
    // Todo el lote comparte `ahora`, y una exportación de Android no trae segundos: sin la
    // posición, los mensajes del mismo minuto no tendrían con qué desempatar al leerse.
    List<MensajeProveedorJpaEntity> filas = new ArrayList<>(mensajes.size());
    for (int i = 0; i < mensajes.size(); i++) {
      filas.add(aFila(mensajes.get(i), ahora, i));
    }
    jpa.saveAll(filas);
  }

  @Override
  public Set<IdExternoDeMensaje> idsExternosExistentes(
      UUID proveedorId, Collection<IdExternoDeMensaje> candidatos) {
    if (candidatos.isEmpty()) {
      return Set.of();
    }
    List<String> valores = candidatos.stream().map(IdExternoDeMensaje::valor).toList();
    return jpa.idsExternosExistentes(proveedorId, valores).stream()
        .map(IdExternoDeMensaje::new)
        .collect(Collectors.toSet());
  }

  @Override
  public List<MensajeProveedor> listarDeLote(UUID loteId) {
    return jpa.findByLoteIdOrderByEnviadoEnAscCreadoEnAscPosicionAsc(loteId).stream()
        .map(RepositorioMensajesProveedorJpa::aDominio)
        .toList();
  }

  @Override
  public List<MensajeProveedor> buscarPorIds(List<UUID> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    Map<UUID, MensajeProveedor> porId =
        jpa.findAllById(ids).stream()
            .map(RepositorioMensajesProveedorJpa::aDominio)
            .collect(Collectors.toMap(MensajeProveedor::id, m -> m));
    return ids.stream().map(porId::get).filter(Objects::nonNull).toList();
  }

  @Override
  public void eliminarTodos(Collection<UUID> ids) {
    jpa.deleteAllById(ids);
  }

  private static MensajeProveedorJpaEntity aFila(
      MensajeProveedor m, Instant creadoEn, int posicion) {
    return new MensajeProveedorJpaEntity(
        m.id(),
        m.proveedorId(),
        m.loteId(),
        m.idExterno().valor(),
        m.enviadoEn(),
        m.tipo().name(),
        m.texto().orElse(null),
        m.pieDeFoto().orElse(null),
        m.referenciaArchivo().orElse(null),
        m.medioOmitido(),
        creadoEn,
        posicion,
        m.pHash().map(PHash::hex).orElse(null));
  }

  private static MensajeProveedor aDominio(MensajeProveedorJpaEntity fila) {
    return new MensajeProveedor(
        fila.getId(),
        fila.getProveedorId(),
        fila.getLoteId(),
        new IdExternoDeMensaje(fila.getIdExterno()),
        fila.getEnviadoEn(),
        TipoMensaje.valueOf(fila.getTipo()),
        fila.getTexto(),
        fila.getPieDeFoto(),
        fila.getReferenciaArchivo(),
        fila.isMedioOmitido(),
        fila.getPhash() == null ? null : PHash.deHex(fila.getPhash()));
  }
}
