package co.tecnosport.api.presentation.sugerencia;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * Sin recurso real que abrir ni cerrar: en este slice de {@code @WebMvcTest} no hay JPA, así que
 * este doble solo deja pasar el callback de {@code TransactionTemplate}. Es el séptimo con este
 * nombre y es copia de los otros seis, a propósito: son de visibilidad de paquete, que es lo que
 * impide que un doble de prueba se filtre a otro slice y lo ate a este.
 */
final class PlatformTransactionManagerDobleDePrueba extends AbstractPlatformTransactionManager {

  @Override
  protected Object doGetTransaction() {
    return new Object();
  }

  @Override
  protected void doBegin(Object transaction, TransactionDefinition definition) {}

  @Override
  protected void doCommit(DefaultTransactionStatus status) {}

  @Override
  protected void doRollback(DefaultTransactionStatus status) {}
}
