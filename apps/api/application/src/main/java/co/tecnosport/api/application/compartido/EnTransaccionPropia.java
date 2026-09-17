package co.tecnosport.api.application.compartido;

import java.util.function.Supplier;

/**
 * Confirma un trozo de trabajo por su cuenta, sin esperar a que termine lo que venga después.
 *
 * <p>Existe por un caso y conviene que siga siendo por ese caso: <strong>la emisión de la guía
 * escribe una fila, después llama a un tercero que cobra, y después escribe otra vez</strong>
 * (adr/0033). Si las dos escrituras viven en la misma transacción, la primera no está confirmada
 * cuando la plataforma cobra, y un reinicio del servicio en ese intervalo deja una guía pagada sin
 * ninguna fila que la nombre — que es exactamente lo que {@code EmisionDeGuia} existe para evitar.
 *
 * <p>Es un puerto y no un {@code TransactionTemplate} porque {@code application} no conoce Spring
 * (regla dura #1). Lo implementa {@code bootstrap}, que sí.
 *
 * <p><strong>No es para acelerar nada.</strong> Partir una transacción es renunciar a la
 * atomicidad, y eso solo se paga cuando el trabajo ya no puede ser atómico de todos modos: aquí no
 * puede, porque en la mitad hay un cobro de un tercero que ninguna transacción de base de datos
 * revierte. Si un caso de uso nuevo quiere esto sin tener un tercero en la mitad, lo que quiere es
 * otra cosa.
 */
public interface EnTransaccionPropia {

  <T> T ejecutar(Supplier<T> trabajo);
}
