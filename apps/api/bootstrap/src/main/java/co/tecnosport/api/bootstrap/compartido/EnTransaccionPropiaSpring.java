package co.tecnosport.api.bootstrap.compartido;

import co.tecnosport.api.application.compartido.EnTransaccionPropia;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code PROPAGATION_REQUIRES_NEW}: si quien llama ya tiene una transacción abierta, esto se
 * confirma igual y por su cuenta. Es lo que hace que la fila de la emisión exista antes de que la
 * plataforma cobre, aunque el endpoint que la pidió todavía esté a medias.
 */
@Component
public class EnTransaccionPropiaSpring implements EnTransaccionPropia {

  private final TransactionTemplate transaccion;

  public EnTransaccionPropiaSpring(PlatformTransactionManager transactionManager) {
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
    this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Override
  public <T> T ejecutar(Supplier<T> trabajo) {
    return transaccion.execute(estado -> trabajo.get());
  }
}
