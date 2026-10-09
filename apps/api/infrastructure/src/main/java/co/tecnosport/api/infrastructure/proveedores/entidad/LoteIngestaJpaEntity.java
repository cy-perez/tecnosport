package co.tecnosport.api.infrastructure.proveedores.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lote_ingesta")
public class LoteIngestaJpaEntity {

  @Id private UUID id;

  @Column(nullable = false)
  private String origen;

  @Column(name = "proveedor_id", nullable = false)
  private UUID proveedorId;

  @Column(name = "referencia_archivo")
  private String referenciaArchivo;

  @Column(nullable = false)
  private String estado;

  @Column(name = "resumen_mensajes_leidos")
  private Integer resumenMensajesLeidos;

  @Column(name = "resumen_mensajes_ignorados")
  private Integer resumenMensajesIgnorados;

  @Column(name = "resumen_mensajes_nuevos")
  private Integer resumenMensajesNuevos;

  @Column(name = "resumen_publicaciones")
  private Integer resumenPublicaciones;

  @Column(name = "resumen_borradores_nuevos")
  private Integer resumenBorradoresNuevos;

  @Column(name = "resumen_renovaciones")
  private Integer resumenRenovaciones;

  @Column(name = "resumen_agotados")
  private Integer resumenAgotados;

  @Column(name = "resumen_descartes")
  private Integer resumenDescartes;

  @Column(name = "resumen_alertas")
  private Integer resumenAlertas;

  @Column(name = "detalle_error")
  private String detalleError;

  @Column(name = "creado_en", nullable = false)
  private Instant creadoEn;

  @Column(name = "iniciado_en")
  private Instant iniciadoEn;

  @Column(name = "terminado_en")
  private Instant terminadoEn;

  @Column(name = "chat_de_caballero", nullable = false)
  private boolean chatDeCaballero;

  protected LoteIngestaJpaEntity() {}

  public LoteIngestaJpaEntity(
      UUID id,
      String origen,
      UUID proveedorId,
      String referenciaArchivo,
      String estado,
      Integer resumenMensajesLeidos,
      Integer resumenMensajesIgnorados,
      Integer resumenMensajesNuevos,
      Integer resumenPublicaciones,
      Integer resumenBorradoresNuevos,
      Integer resumenRenovaciones,
      Integer resumenAgotados,
      Integer resumenDescartes,
      Integer resumenAlertas,
      String detalleError,
      Instant creadoEn,
      Instant iniciadoEn,
      Instant terminadoEn,
      boolean chatDeCaballero) {
    this.id = id;
    this.origen = origen;
    this.proveedorId = proveedorId;
    this.referenciaArchivo = referenciaArchivo;
    this.estado = estado;
    this.resumenMensajesLeidos = resumenMensajesLeidos;
    this.resumenMensajesIgnorados = resumenMensajesIgnorados;
    this.resumenMensajesNuevos = resumenMensajesNuevos;
    this.resumenPublicaciones = resumenPublicaciones;
    this.resumenBorradoresNuevos = resumenBorradoresNuevos;
    this.resumenRenovaciones = resumenRenovaciones;
    this.resumenAgotados = resumenAgotados;
    this.resumenDescartes = resumenDescartes;
    this.resumenAlertas = resumenAlertas;
    this.detalleError = detalleError;
    this.creadoEn = creadoEn;
    this.iniciadoEn = iniciadoEn;
    this.terminadoEn = terminadoEn;
    this.chatDeCaballero = chatDeCaballero;
  }

  public boolean isChatDeCaballero() {
    return chatDeCaballero;
  }

  public UUID getId() {
    return id;
  }

  public String getOrigen() {
    return origen;
  }

  public UUID getProveedorId() {
    return proveedorId;
  }

  public String getReferenciaArchivo() {
    return referenciaArchivo;
  }

  public String getEstado() {
    return estado;
  }

  public Integer getResumenMensajesLeidos() {
    return resumenMensajesLeidos;
  }

  public Integer getResumenMensajesIgnorados() {
    return resumenMensajesIgnorados;
  }

  public Integer getResumenMensajesNuevos() {
    return resumenMensajesNuevos;
  }

  public Integer getResumenPublicaciones() {
    return resumenPublicaciones;
  }

  public Integer getResumenBorradoresNuevos() {
    return resumenBorradoresNuevos;
  }

  public Integer getResumenRenovaciones() {
    return resumenRenovaciones;
  }

  public Integer getResumenAgotados() {
    return resumenAgotados;
  }

  public Integer getResumenDescartes() {
    return resumenDescartes;
  }

  public Integer getResumenAlertas() {
    return resumenAlertas;
  }

  public String getDetalleError() {
    return detalleError;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }

  public Instant getIniciadoEn() {
    return iniciadoEn;
  }

  public Instant getTerminadoEn() {
    return terminadoEn;
  }
}
