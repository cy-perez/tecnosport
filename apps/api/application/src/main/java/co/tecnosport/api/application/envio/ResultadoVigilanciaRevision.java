package co.tecnosport.api.application.envio;

/**
 * Cuántas cosas llevaban demasiado tiempo esperando y de cuántas se avisó. Los dos números no
 * coinciden cuando ya se había avisado de algo en una vuelta anterior, que es el caso normal: la
 * tarea corre seguido y el correo sale una sola vez por novedad.
 */
public record ResultadoVigilanciaRevision(int vencidas, int avisadas) {}
