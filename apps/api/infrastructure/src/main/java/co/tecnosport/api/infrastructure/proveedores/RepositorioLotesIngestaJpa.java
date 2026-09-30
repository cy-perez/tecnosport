package co.tecnosport.api.infrastructure.proveedores;

import co.tecnosport.api.application.proveedores.LotesPaginados;
import co.tecnosport.api.application.proveedores.RepositorioLotesIngesta;
import co.tecnosport.api.domain.proveedores.EstadoLote;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.OrigenIngesta;
import co.tecnosport.api.domain.proveedores.ResumenIngesta;
import co.tecnosport.api.infrastructure.proveedores.entidad.LoteIngestaJpaEntity;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioLotesIngestaJpa implements RepositorioLotesIngesta {

  private final LoteIngestaJpaRepository jpa;

  public RepositorioLotesIngestaJpa(LoteIngestaJpaRepository jpa) {
    this.jpa = Objects.requireNonNull(jpa);
  }

  @Override
  public void guardar(LoteIngesta lote) {
    jpa.save(aFila(lote));
  }

  @Override
  public void actualizar(LoteIngesta lote) {
    jpa.save(aFila(lote));
  }

  @Override
  public Optional<LoteIngesta> buscarPorId(UUID id) {
    return jpa.findById(id).map(RepositorioLotesIngestaJpa::aDominio);
  }

  @Override
  public LotesPaginados listar(UUID proveedorId, int pagina, int tamanoPagina) {
    PageRequest peticion =
        PageRequest.of(pagina, tamanoPagina, Sort.by(Sort.Direction.DESC, "creadoEn"));
    Page<LoteIngestaJpaEntity> resultado =
        proveedorId == null ? jpa.findAll(peticion) : jpa.findByProveedorId(proveedorId, peticion);
    return new LotesPaginados(
        resultado.getContent().stream().map(RepositorioLotesIngestaJpa::aDominio).toList(),
        resultado.getNumber(),
        resultado.getTotalPages(),
        resultado.getTotalElements());
  }

  private static LoteIngestaJpaEntity aFila(LoteIngesta lote) {
    ResumenIngesta r = lote.resumen().orElse(null);
    return new LoteIngestaJpaEntity(
        lote.id(),
        lote.origen().name(),
        lote.proveedorId(),
        lote.referenciaArchivo().orElse(null),
        lote.estado().name(),
        r == null ? null : r.mensajesLeidos(),
        r == null ? null : r.mensajesIgnorados(),
        r == null ? null : r.mensajesNuevos(),
        r == null ? null : r.publicaciones(),
        r == null ? null : r.borradoresNuevos(),
        r == null ? null : r.renovaciones(),
        r == null ? null : r.agotados(),
        r == null ? null : r.descartes(),
        r == null ? null : r.alertas(),
        lote.detalleError().orElse(null),
        lote.creadoEn(),
        lote.iniciadoEn().orElse(null),
        lote.terminadoEn().orElse(null));
  }

  private static LoteIngesta aDominio(LoteIngestaJpaEntity fila) {
    ResumenIngesta resumen =
        fila.getResumenMensajesLeidos() == null
            ? null
            : new ResumenIngesta(
                fila.getResumenMensajesLeidos(),
                fila.getResumenMensajesIgnorados(),
                fila.getResumenMensajesNuevos(),
                fila.getResumenPublicaciones(),
                fila.getResumenBorradoresNuevos(),
                fila.getResumenRenovaciones(),
                fila.getResumenAgotados(),
                fila.getResumenDescartes(),
                fila.getResumenAlertas());
    return new LoteIngesta(
        fila.getId(),
        OrigenIngesta.valueOf(fila.getOrigen()),
        fila.getProveedorId(),
        fila.getReferenciaArchivo(),
        EstadoLote.valueOf(fila.getEstado()),
        resumen,
        fila.getDetalleError(),
        fila.getCreadoEn(),
        fila.getIniciadoEn(),
        fila.getTerminadoEn());
  }
}
