package co.tecnosport.api.bootstrap.envio;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * El vigilante de los cobros extra de la transportadora: cuántos días atrás se pregunta, cada
 * cuánto, y a quién se le avisa.
 *
 * <p><strong>No hay umbral, y la ausencia es la decisión.</strong> Se avisa de todos los cobros
 * porque son raros y cada uno se come saldo en silencio: no hay una cifra por debajo de la cual
 * convenga callar, y ponerla sería inventar un dato de negocio que nadie ha decidido. Lo que evita
 * el ruido es que cada cobro se avisa una sola vez, y eso lo garantiza la tabla, no una cifra.
 *
 * <p>{@code dias-atras} no es un dato de negocio tampoco: es el ancho de la ventana que se
 * consulta. Treinta días porque la transportadora reliquida semanas después de la entrega y porque
 * el orden en que la plataforma devuelve los cobros no está documentado — preguntar por una ventana
 * es lo único que no se apoya en un orden que nadie prometió. Volver a leer la misma ventana cada
 * vuelta no cuesta nada: de lo ya avisado no se avisa dos veces.
 *
 * <p>Y el intervalo, veinticuatro horas: el cargo aparece días después del despacho, así que
 * preguntar más seguido no adelantaría nada y gastaría cuota de un proveedor limitado a dos
 * peticiones por segundo. Es el vigilante menos urgente de los tres — nadie se queda sin despachar
 * por esto; lo que se pierde es saber cuánto costó de verdad un pedido.
 *
 * <p>El destinatario lleva valor por omisión real, igual que los otros dos avisos: es el correo del
 * negocio, ya publicado en el pie del sitio y en los legales.
 */
@ConfigurationProperties(prefix = "tecnosport.sobrecostos-envios")
public record PropiedadesSobrecostosEnvios(
    int diasAtras, int intervaloMinutos, int retrasoInicialMinutos, String destinatario) {

  public PropiedadesSobrecostosEnvios {
    if (diasAtras <= 0) {
      throw new IllegalStateException(
          "tecnosport.sobrecostos-envios.dias-atras debe ser mayor que cero: una ventana vacía no"
              + " pregunta por nada y el vigilante no vigilaría nada.");
    }
    if (intervaloMinutos <= 0) {
      throw new IllegalStateException(
          "tecnosport.sobrecostos-envios.intervalo-minutos debe ser mayor que cero.");
    }
    if (retrasoInicialMinutos <= 0 || retrasoInicialMinutos >= intervaloMinutos) {
      throw new IllegalStateException(
          "tecnosport.sobrecostos-envios.retraso-inicial-minutos debe ser mayor que cero y menor que"
              + " el intervalo: si lo iguala, cada despliegue reinicia la cuenta y la tarea puede no"
              + " correr nunca.");
    }
    if (destinatario == null || destinatario.isBlank()) {
      throw new IllegalStateException(
          "tecnosport.sobrecostos-envios.destinatario no puede estar vacío: un aviso sin"
              + " destinatario no avisa a nadie.");
    }
  }
}
