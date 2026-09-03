package co.tecnosport.api.presentation.pago;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * Sin recurso real que abrir ni cerrar: en este slice de {@code @WebMvcTest} no hay JPA, así que
 * este doble solo deja pasar el callback de {@code TransactionTemplate}. La atomicidad real la
 * prueba {@code RepositorioPagosJpaTest} contra Postgres.
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
