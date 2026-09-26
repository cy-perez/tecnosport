package co.tecnosport.api.presentation.sugerencia;

import co.tecnosport.api.application.sugerencia.EnviarSugerencia;
import co.tecnosport.api.application.sugerencia.EnviarSugerenciaComando;
import co.tecnosport.api.presentation.compartido.IpDelCliente;
import co.tecnosport.api.presentation.sugerencia.dto.EnviarSugerenciaRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El buzón de sugerencias, público y sin sesión: escribir una opinión no puede exigir una cuenta.
 *
 * <p>Queda abierto por el {@code anyRequest().permitAll()} de {@code ConfiguracionSeguridad}, como
 * el resto de lo que no cuelga de {@code /api/v1/admin}. Lo que sí lleva puesto es el límite de
 * intentos por IP, que {@code ConfiguracionLimiteIntentos} engancha a esta ruta por su nombre: un
 * POST público sin límite es un formulario de spam esperando a que alguien lo encuentre.
 *
 * <p>Responde {@code 202 Accepted} y no {@code 201}: no hay recurso que el cliente pueda ir a
 * consultar después —el buzón no se lee desde fuera— y lo que se acepta es el mensaje, no la
 * creación de algo que se le vaya a devolver. Tampoco devuelve el id: darle a quien escribe un
 * identificador sugeriría que puede hacer seguimiento, y no puede; para lo que sí tiene seguimiento
 * —garantía, retracto, reclamo, datos personales— está el canal de contacto, y la propia pantalla
 * lo dice.
 */
@RestController
@RequestMapping("/api/v1/sugerencias")
public class SugerenciasControlador {

  private final EnviarSugerencia enviarSugerencia;
  private final TransactionTemplate transaccion;

  public SugerenciasControlador(
      EnviarSugerencia enviarSugerencia, PlatformTransactionManager transactionManager) {
    this.enviarSugerencia = Objects.requireNonNull(enviarSugerencia);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  @PostMapping
  public ResponseEntity<Void> enviar(
      @RequestBody EnviarSugerenciaRequest cuerpo, HttpServletRequest peticion) {
    String ip = IpDelCliente.de(peticion);
    transaccion.executeWithoutResult(
        estado ->
            enviarSugerencia.ejecutar(
                new EnviarSugerenciaComando(
                    cuerpo.mensaje(), cuerpo.correo(), cuerpo.autorizaDatos(), ip)));
    return ResponseEntity.status(HttpStatus.ACCEPTED).build();
  }
}
