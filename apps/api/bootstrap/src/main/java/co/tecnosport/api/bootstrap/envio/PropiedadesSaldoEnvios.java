package co.tecnosport.api.bootstrap.envio;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * El vigilante del crédito de la plataforma de envíos: por debajo de cuánto hay que avisar, cada
 * cuánto se mira, y a quién.
 *
 * <p><strong>{@code umbral-cop} es un dato de negocio y está decidido: 50.000.</strong> No es una
 * cifra redonda elegida por cómoda — es lo que alcanza para unas seis guías baratas o dos caras
 * (8.200 la más barata que de verdad emite, 19.465 la más cara, medidas contra la cuenta) y es el
 * tamaño de la recarga que Skydropx hizo el 16 de septiembre de 2026. Da margen para pedir la
 * recarga y para que llegue, que es lo único que este aviso tiene que conseguir: entre pedirla y
 * recibirla pasan días, y la recarga por Mercado Pago nunca funcionó.
 *
 * <p>El intervalo, en cambio, no es de negocio: es cada cuánto se pregunta. Doce horas porque el
 * saldo solo baja al emitir, emitir lo dispara una persona desde el panel, y la respuesta al aviso
 * —pedirle una recarga a Skydropx— tarda más de un día. Preguntar más seguido no adelantaría nada y
 * gastaría cuota de un proveedor limitado a dos peticiones por segundo.
 *
 * <p>Doce horas es además lo que evita que el aviso se vuelva ruido, porque el caso de uso no
 * recuerda de qué ya avisó: mientras la cuenta siga por debajo del umbral, salen dos correos al día
 * diciendo lo mismo. Es deliberado — el mensaje es que la cuenta sigue sin plata.
 *
 * <p>El destinatario lleva valor por omisión real, igual que el de la bandeja de revisión: es el
 * correo del negocio, ya publicado en el pie del sitio y en los legales.
 */
@ConfigurationProperties(prefix = "tecnosport.saldo-envios")
public record PropiedadesSaldoEnvios(
    long umbralCop, int intervaloMinutos, int retrasoInicialMinutos, String destinatario) {

  public PropiedadesSaldoEnvios {
    if (umbralCop <= 0) {
      throw new IllegalStateException(
          "tecnosport.saldo-envios.umbral-cop debe ser mayor que cero: un umbral en cero nunca"
              + " avisa, y el despacho se enteraría igual que antes — con una emisión que falla.");
    }
    if (intervaloMinutos <= 0) {
      throw new IllegalStateException(
          "tecnosport.saldo-envios.intervalo-minutos debe ser mayor que cero.");
    }
    if (retrasoInicialMinutos <= 0 || retrasoInicialMinutos >= intervaloMinutos) {
      throw new IllegalStateException(
          "tecnosport.saldo-envios.retraso-inicial-minutos debe ser mayor que cero y menor que el"
              + " intervalo: si lo iguala, cada despliegue reinicia la cuenta y la tarea puede no"
              + " correr nunca.");
    }
    if (destinatario == null || destinatario.isBlank()) {
      throw new IllegalStateException(
          "tecnosport.saldo-envios.destinatario no puede estar vacío: un aviso sin destinatario no"
              + " avisa a nadie.");
    }
  }
}
