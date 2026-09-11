package co.tecnosport.api.infrastructure.pedido.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** {@code Direccion} aplanada en columnas nulas cuando {@code tipoEntrega == RETIRO_EN_PUNTO}. */
@Entity
@Table(name = "pedido")
public class PedidoJpaEntity {

  @Id private UUID id;

  @Column(name = "numero_pedido", nullable = false, unique = true)
  private String numeroPedido;

  @Column(name = "usuario_id")
  private UUID usuarioId;

  @Column(nullable = false)
  private String correo;

  @Column(name = "tipo_entrega", nullable = false)
  private String tipoEntrega;

  @Column(name = "codigo_dane_departamento")
  private String codigoDaneDepartamento;

  private String departamento;

  @Column(name = "codigo_dane_ciudad")
  private String codigoDaneCiudad;

  private String ciudad;

  private String direccion;

  private String indicaciones;

  @Column(name = "metodo_pago", nullable = false)
  private String metodoPago;

  @Column(nullable = false)
  private String estado;

  @Column(name = "creado_en", nullable = false)
  private Instant creadoEn;

  @Column(name = "aviso_plazo_entrega_enviado_en")
  private Instant avisoPlazoEntregaEnviadoEn;

  protected PedidoJpaEntity() {}

  public PedidoJpaEntity(
      UUID id,
      String numeroPedido,
      UUID usuarioId,
      String correo,
      String tipoEntrega,
      String codigoDaneDepartamento,
      String departamento,
      String codigoDaneCiudad,
      String ciudad,
      String direccion,
      String indicaciones,
      String metodoPago,
      String estado,
      Instant creadoEn,
      Instant avisoPlazoEntregaEnviadoEn) {
    this.id = id;
    this.numeroPedido = numeroPedido;
    this.usuarioId = usuarioId;
    this.correo = correo;
    this.tipoEntrega = tipoEntrega;
    this.codigoDaneDepartamento = codigoDaneDepartamento;
    this.departamento = departamento;
    this.codigoDaneCiudad = codigoDaneCiudad;
    this.ciudad = ciudad;
    this.direccion = direccion;
    this.indicaciones = indicaciones;
    this.metodoPago = metodoPago;
    this.estado = estado;
    this.creadoEn = creadoEn;
    this.avisoPlazoEntregaEnviadoEn = avisoPlazoEntregaEnviadoEn;
  }

  public UUID getId() {
    return id;
  }

  public String getNumeroPedido() {
    return numeroPedido;
  }

  public UUID getUsuarioId() {
    return usuarioId;
  }

  public String getCorreo() {
    return correo;
  }

  public String getTipoEntrega() {
    return tipoEntrega;
  }

  public String getCodigoDaneDepartamento() {
    return codigoDaneDepartamento;
  }

  public String getDepartamento() {
    return departamento;
  }

  public String getCodigoDaneCiudad() {
    return codigoDaneCiudad;
  }

  public String getCiudad() {
    return ciudad;
  }

  public String getDireccion() {
    return direccion;
  }

  public String getIndicaciones() {
    return indicaciones;
  }

  public String getMetodoPago() {
    return metodoPago;
  }

  public String getEstado() {
    return estado;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }

  public Instant getAvisoPlazoEntregaEnviadoEn() {
    return avisoPlazoEntregaEnviadoEn;
  }
}
