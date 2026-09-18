package co.tecnosport.api.application.envio;

/**
 * Qué se vio al preguntar por los cobros extra. Los tres desenlaces se distinguen por lo mismo que
 * en {@link ResultadoVigilanciaSaldo}: el registro de la tarea tiene que poder separar "no hay
 * cobros nuevos" de "no se pudo preguntar", porque lo segundo repetido durante días significa que
 * la vigilancia no está vigilando nada.
 *
 * @param seSupo falso solo cuando la plataforma no contestó
 * @param encontrados cuántos cobros trajo la ventana consultada, ya avisados o no
 * @param avisados de cuántos se avisó en esta vuelta
 */
public record ResultadoVigilanciaSobrecostos(boolean seSupo, int encontrados, int avisados) {

  public static ResultadoVigilanciaSobrecostos noSeSabe() {
    return new ResultadoVigilanciaSobrecostos(false, 0, 0);
  }

  public static ResultadoVigilanciaSobrecostos sinNovedad(int encontrados) {
    return new ResultadoVigilanciaSobrecostos(true, encontrados, 0);
  }

  public static ResultadoVigilanciaSobrecostos avisado(int encontrados, int avisados) {
    return new ResultadoVigilanciaSobrecostos(true, encontrados, avisados);
  }
}
