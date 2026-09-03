package co.tecnosport.api.presentation.usuario;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

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
