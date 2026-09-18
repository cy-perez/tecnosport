package co.tecnosport.api.application.compartido;

/**
 * Cada texto que este sistema le manda por correo a una persona, por su llave.
 *
 * <p>Un enum y no cadenas sueltas por dos razones, y la segunda pesa más: una llave mal escrita no
 * compila, y quien resuelve los textos puede recorrer {@code values()} al arrancar y negarse a
 * levantar el servicio si falta alguno. Con llaves sueltas, un texto que falta se descubre el día
 * que un comprador tenía que recibirlo — y varios de estos son la constancia de que se le devolvió
 * el dinero o de que su PQR quedó radicada.
 *
 * <p>Los textos <b>no viven aquí</b>: viven en {@code correos_es.properties} y {@code
 * correos_en.properties}, fuera del código, como manda la regla dura #4 del CLAUDE.md. Antes se
 * concatenaban dentro de cada caso de uso, en un solo idioma y —salvo los dos de la Fase 4— sin una
 * sola tilde, citando artículos de la Ley 1480 con faltas de ortografía.
 */
public enum TextoDeCorreo {
  RETRACTO_ACUSE_ASUNTO("retracto.acuse.asunto"),
  RETRACTO_ACUSE_CUERPO("retracto.acuse.cuerpo"),
  RETRACTO_REINTEGRO_ASUNTO("retracto.reintegro.asunto"),
  RETRACTO_REINTEGRO_CUERPO("retracto.reintegro.cuerpo"),
  PEDIDO_CANCELACION_ASUNTO("pedido.cancelacion.asunto"),
  PEDIDO_CANCELACION_NO_DISPONIBILIDAD("pedido.cancelacion.no_disponibilidad"),
  PEDIDO_CANCELACION_PLAZO_INCUMPLIDO("pedido.cancelacion.plazo_incumplido"),
  PEDIDO_CANCELACION_CON_REINTEGRO("pedido.cancelacion.con_reintegro"),
  PEDIDO_CANCELACION_SIN_COBRO("pedido.cancelacion.sin_cobro"),
  PEDIDO_CANCELACION_CIERRE("pedido.cancelacion.cierre"),
  PEDIDO_DESPACHO_ASUNTO("pedido.despacho.asunto"),
  PEDIDO_DESPACHO_CUERPO("pedido.despacho.cuerpo"),
  PEDIDO_DESPACHO_CUERPO_VARIAS("pedido.despacho.cuerpo_varias"),
  PEDIDO_PLAZO_VENCIDO_ASUNTO("pedido.plazo_vencido.asunto"),
  PEDIDO_PLAZO_VENCIDO_CUERPO("pedido.plazo_vencido.cuerpo"),
  PEDIDO_PLAZO_VENCIDO_EN_CAMINO("pedido.plazo_vencido.en_camino"),
  PEDIDO_PLAZO_VENCIDO_CON_DINERO("pedido.plazo_vencido.con_dinero"),
  PEDIDO_PLAZO_VENCIDO_SIN_COBRO("pedido.plazo_vencido.sin_cobro"),
  PEDIDO_PLAZO_VENCIDO_CIERRE("pedido.plazo_vencido.cierre"),
  ATENCION_ACUSE_ASUNTO("atencion.acuse.asunto"),
  ATENCION_ACUSE_CUERPO("atencion.acuse.cuerpo"),
  ATENCION_PRORROGA_ASUNTO("atencion.prorroga.asunto"),
  ATENCION_PRORROGA_CUERPO("atencion.prorroga.cuerpo"),
  ENVIO_REVISION_PENDIENTE_ASUNTO("envio.revision_pendiente.asunto"),
  ENVIO_REVISION_PENDIENTE_CUERPO("envio.revision_pendiente.cuerpo"),
  ENVIO_REVISION_PENDIENTE_GUIA("envio.revision_pendiente.guia"),
  ENVIO_REVISION_PENDIENTE_EMISION("envio.revision_pendiente.emision"),
  ENVIO_REVISION_PENDIENTE_CIERRE("envio.revision_pendiente.cierre"),
  ENVIO_SALDO_BAJO_ASUNTO("envio.saldo_bajo.asunto"),
  ENVIO_SALDO_BAJO_CUERPO("envio.saldo_bajo.cuerpo"),
  ENVIO_SOBRECOSTO_ASUNTO("envio.sobrecosto.asunto"),
  ENVIO_SOBRECOSTO_CUERPO("envio.sobrecosto.cuerpo"),
  ENVIO_SOBRECOSTO_COBRO("envio.sobrecosto.cobro"),
  ENVIO_SOBRECOSTO_CIERRE("envio.sobrecosto.cierre"),
  ENVIO_SOBRECOSTO_SIN_GUIA("envio.sobrecosto.sin_guia"),
  ENVIO_SOBRECOSTO_SIN_PEDIDO("envio.sobrecosto.sin_pedido"),
  ENVIO_SOBRECOSTO_SIN_FECHA("envio.sobrecosto.sin_fecha"),
  USUARIO_VERIFICACION_ASUNTO("usuario.verificacion.asunto"),
  USUARIO_VERIFICACION_CUERPO("usuario.verificacion.cuerpo"),
  USUARIO_RECUPERACION_ASUNTO("usuario.recuperacion.asunto"),
  USUARIO_RECUPERACION_CUERPO("usuario.recuperacion.cuerpo");

  private final String clave;

  TextoDeCorreo(String clave) {
    this.clave = clave;
  }

  public String clave() {
    return clave;
  }
}
