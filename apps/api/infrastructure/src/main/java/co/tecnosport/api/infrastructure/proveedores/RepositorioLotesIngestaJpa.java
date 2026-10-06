package co.tecnosport.api.infrastructure.proveedores;

import co.tecnosport.api.application.proveedores.DependenciasDeLote;
import co.tecnosport.api.application.proveedores.LotesPaginados;
import co.tecnosport.api.application.proveedores.RepositorioLotesIngesta;
import co.tecnosport.api.domain.proveedores.EstadoLote;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.OrigenIngesta;
import co.tecnosport.api.domain.proveedores.ResumenIngesta;
import co.tecnosport.api.infrastructure.proveedores.entidad.LoteIngestaJpaEntity;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioLotesIngestaJpa implements RepositorioLotesIngesta {

  /**
   * El orden es el de las llaves foráneas, todas {@code restrict} salvo la de {@code
   * publicacion_mensaje}, que cae en cascada con su publicación. Como en {@code
   * RepositorioProveedoresJpa}, pero por lote.
   */
  private static final List<String> BORRAR_HISTORIAL =
      List.of(
          "delete from borrador_producto where publicacion_id in"
              + " (select id from publicacion_proveedor where lote_id = ?1)",
          "delete from publicacion_proveedor where lote_id = ?1",
          "delete from mensaje_proveedor where lote_id = ?1",
          "delete from lote_ingesta where id = ?1");

  private final LoteIngestaJpaRepository jpa;
  private final EntityManager entityManager;

  public RepositorioLotesIngestaJpa(LoteIngestaJpaRepository jpa, EntityManager entityManager) {
    this.jpa = Objects.requireNonNull(jpa);
    this.entityManager = Objects.requireNonNull(entityManager);
  }

  @Override
  public DependenciasDeLote dependenciasDe(UUID loteId) {
    @SuppressWarnings("unchecked")
    List<Object> productos =
        entityManager
            .createNativeQuery(
                "select distinct b.producto_id from borrador_producto b"
                    + " join publicacion_proveedor p on p.id = b.publicacion_id"
                    + " where p.lote_id = ?1 and b.estado = 'APROBADO'"
                    + " and b.producto_id is not null")
            .setParameter(1, loteId)
            .getResultList();
    @SuppressWarnings("unchecked")
    List<String> archivos =
        entityManager
            .createNativeQuery(
                // El ZIP solo si ningún otro lote lo nombra: un envío repetido de la misma
                // exportación crea dos lotes sobre el mismo objeto.
                "select l.referencia_archivo from lote_ingesta l"
                    + " where l.id = ?1 and l.referencia_archivo is not null"
                    + " and not exists (select 1 from lote_ingesta o"
                    + " where o.referencia_archivo = l.referencia_archivo and o.id <> l.id)"
                    + " union"
                    + " select referencia_archivo from mensaje_proveedor"
                    + " where lote_id = ?1 and referencia_archivo is not null"
                    // Las que se subieron desde el panel a los borradores del lote: la fila se va
                    // en cascada con el borrador, y el archivo no lo nombraría nadie más.
                    + " union"
                    + " select f.referencia_archivo from borrador_foto_subida f"
                    + " join borrador_producto b on b.id = f.borrador_id"
                    + " join publicacion_proveedor p on p.id = b.publicacion_id"
                    + " where p.lote_id = ?1")
            .setParameter(1, loteId)
            .getResultList();
    return new DependenciasDeLote(
        productos.stream()
            .map(id -> id instanceof UUID uuid ? uuid : UUID.fromString(id.toString()))
            .sorted()
            .toList(),
        archivos.stream().sorted().toList());
  }

  @Override
  public void eliminarConSuHistorial(UUID loteId) {
    // Lo pendiente de esta transacción, antes del `clear()` de abajo, que lo descartaría en
    // silencio.
    entityManager.flush();
    for (String sentencia : BORRAR_HISTORIAL) {
      entityManager.createNativeQuery(sentencia).setParameter(1, loteId).executeUpdate();
    }
    // Las sentencias nativas no pasan por el contexto de persistencia: sin esto, el lote ya cargado
    // en esta transacción seguiría devolviéndose como si existiera.
    entityManager.clear();
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
  public List<LoteIngesta> abiertos() {
    return jpa
        .findByEstadoInOrderByCreadoEnAsc(
            List.of(EstadoLote.RECIBIDO.name(), EstadoLote.PROCESANDO.name()))
        .stream()
        .map(RepositorioLotesIngestaJpa::aDominio)
        .toList();
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
