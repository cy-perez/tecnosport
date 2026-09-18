package co.tecnosport.api.infrastructure.pedido.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
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

  private String barrio;

  @Column(name = "metodo_pago", nullable = false)
  private String metodoPago;

  @Column(nullable = false)
  private String estado;

  @Column(name = "creado_en", nullable = false)
  private Instant creadoEn;

  /**
   * {@code insertable = false, updatable = false}: el único que escribe esta columna es la
   * sentencia condicional de {@code PedidoJpaRepository.reclamarAvisoDePlazo}. Sin eso, {@code
   * guardar} la incluiría en cada actualización del pedido, y una acción del panel hecha sobre un
   * agregado leído antes del reclamo lo dejaría otra vez en nulo — el comprador recibiría el mismo
   * correo dos veces. Lo levantó una revisión adversarial del propio vigilante.
   */
  @Column(name = "aviso_plazo_entrega_enviado_en", insertable = false, updatable = false)
  private Instant avisoPlazoEntregaEnviadoEn;

  /**
   * Cuándo se le mandó al comprador el comprobante de su compra. Mismas dos banderas y por el mismo
   * motivo que el campo de arriba: la escribe únicamente el reclamo condicional de {@code
   * PedidoJpaRepository.reclamarComprobante}.
   *
   * <p>Sin acceso de lectura a propósito, y aquí se separa del anterior: {@code Pedido} no lleva
   * este dato porque <b>ninguna regla del dominio decide nada con él</b>. Es la marca de un efecto
   * hacia afuera, igual que un reclamo, y meterlo en el agregado habría obligado a tocar sus cuatro
   * constructores y todos sus constructores de prueba para que ninguna regla lo usara. Hibernate lo
   * puebla por campo al leer, que es todo lo que las dos consultas necesitan.
   */
  @Column(name = "comprobante_enviado_en", insertable = false, updatable = false)
  @SuppressWarnings("unused")
  private Instant comprobanteEnviadoEn;

  /**
   * Lo que el comprador paga de flete, congelado al confirmar. No confundir con {@code
   * EnvioJpaEntity.costoEnvio}, que es lo que el despacho le cuesta al negocio.
   */
  @Column(name = "costo_envio", nullable = false)
  private BigDecimal costoEnvio;

  @Column(name = "tarifa_envio_id")
  private String tarifaEnvioId;

  @Column(name = "tarifa_envio_transportadora")
  private String tarifaEnvioTransportadora;

  @Column(name = "tarifa_envio_servicio")
  private String tarifaEnvioServicio;

  @Column(name = "tarifa_envio_dias")
  private Integer tarifaEnvioDias;

  @Column(name = "tarifa_envio_admite_contraentrega")
  private Boolean tarifaEnvioAdmiteContraentrega;

  @Column(name = "tarifa_envio_vence_en")
  private Instant tarifaEnvioVenceEn;

  @Column(name = "nombre_contacto")
  private String nombreContacto;

  @Column(name = "telefono_contacto")
  private String telefonoContacto;

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
      String barrio,
      String metodoPago,
      String estado,
      Instant creadoEn,
      Instant avisoPlazoEntregaEnviadoEn,
      BigDecimal costoEnvio,
      String tarifaEnvioId,
      String tarifaEnvioTransportadora,
      String tarifaEnvioServicio,
      Integer tarifaEnvioDias,
      Boolean tarifaEnvioAdmiteContraentrega,
      Instant tarifaEnvioVenceEn,
      String nombreContacto,
      String telefonoContacto) {
    this.nombreContacto = nombreContacto;
    this.telefonoContacto = telefonoContacto;
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
    this.barrio = barrio;
    this.metodoPago = metodoPago;
    this.estado = estado;
    this.creadoEn = creadoEn;
    this.avisoPlazoEntregaEnviadoEn = avisoPlazoEntregaEnviadoEn;
    this.costoEnvio = costoEnvio;
    this.tarifaEnvioId = tarifaEnvioId;
    this.tarifaEnvioTransportadora = tarifaEnvioTransportadora;
    this.tarifaEnvioServicio = tarifaEnvioServicio;
    this.tarifaEnvioDias = tarifaEnvioDias;
    this.tarifaEnvioAdmiteContraentrega = tarifaEnvioAdmiteContraentrega;
    this.tarifaEnvioVenceEn = tarifaEnvioVenceEn;
  }

  public BigDecimal getCostoEnvio() {
    return costoEnvio;
  }

  public String getTarifaEnvioId() {
    return tarifaEnvioId;
  }

  public String getTarifaEnvioTransportadora() {
    return tarifaEnvioTransportadora;
  }

  public String getTarifaEnvioServicio() {
    return tarifaEnvioServicio;
  }

  public Integer getTarifaEnvioDias() {
    return tarifaEnvioDias;
  }

  public Boolean getTarifaEnvioAdmiteContraentrega() {
    return tarifaEnvioAdmiteContraentrega;
  }

  public Instant getTarifaEnvioVenceEn() {
    return tarifaEnvioVenceEn;
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

  /** El {@code area_level3} de la plataforma de envíos. Opcional: ver {@code Direccion}. */
  public String getBarrio() {
    return barrio;
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

  public String getNombreContacto() {
    return nombreContacto;
  }

  public String getTelefonoContacto() {
    return telefonoContacto;
  }
}
