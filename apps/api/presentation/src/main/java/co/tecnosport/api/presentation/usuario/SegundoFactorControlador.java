package co.tecnosport.api.presentation.usuario;

import co.tecnosport.api.application.usuario.ActivacionConfirmada;
import co.tecnosport.api.application.usuario.ActivacionIniciada;
import co.tecnosport.api.application.usuario.ClaveYCodigoSegundoFactorComando;
import co.tecnosport.api.application.usuario.ConfirmarActivacionSegundoFactor;
import co.tecnosport.api.application.usuario.ConfirmarActivacionSegundoFactorComando;
import co.tecnosport.api.application.usuario.ConsultarSegundoFactor;
import co.tecnosport.api.application.usuario.DesactivarSegundoFactor;
import co.tecnosport.api.application.usuario.IniciarActivacionSegundoFactor;
import co.tecnosport.api.application.usuario.IniciarActivacionSegundoFactorComando;
import co.tecnosport.api.application.usuario.RegenerarCodigosDeRespaldo;
import co.tecnosport.api.application.usuario.SesionDeRefrescoInvalidaException;
import co.tecnosport.api.application.usuario.TokensDeSesion;
import co.tecnosport.api.application.usuario.VerificarSegundoFactor;
import co.tecnosport.api.application.usuario.VerificarSegundoFactorComando;
import co.tecnosport.api.presentation.compartido.CuboDeIp;
import co.tecnosport.api.presentation.compartido.IpDelCliente;
import co.tecnosport.api.presentation.usuario.dto.ActivacionConfirmadaRespuesta;
import co.tecnosport.api.presentation.usuario.dto.ActivacionSegundoFactorRespuesta;
import co.tecnosport.api.presentation.usuario.dto.ClaveYCodigoSegundoFactorRequest;
import co.tecnosport.api.presentation.usuario.dto.CodigoSegundoFactorRequest;
import co.tecnosport.api.presentation.usuario.dto.CodigosDeRespaldoRegeneradosRespuesta;
import co.tecnosport.api.presentation.usuario.dto.EstadoSegundoFactorRespuesta;
import co.tecnosport.api.presentation.usuario.dto.IniciarActivacionSegundoFactorRequest;
import co.tecnosport.api.presentation.usuario.dto.SesionRespuesta;
import co.tecnosport.api.presentation.usuario.dto.VerificarSegundoFactorRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El segundo factor del panel (ADR-0096). Bajo {@code /auth} y no bajo {@code /admin}, y por eso no
 * se llama {@code Admin...}: un administrador con la sesión restringida —el panel le exige el
 * factor y todavía no lo tiene— no entra a {@code /admin/**}, y tiene que poder llegar aquí. Quién
 * entra lo decide {@code ConfiguracionSeguridad}, en bootstrap.
 */
@RestController
@RequestMapping("/api/v1/auth/segundo-factor")
public class SegundoFactorControlador {

  private final IniciarActivacionSegundoFactor iniciarActivacion;
  private final ConfirmarActivacionSegundoFactor confirmarActivacion;
  private final VerificarSegundoFactor verificar;
  private final ConsultarSegundoFactor consultar;
  private final RegenerarCodigosDeRespaldo regenerarCodigos;
  private final DesactivarSegundoFactor desactivar;
  private final TransactionTemplate transaccion;

  public SegundoFactorControlador(
      IniciarActivacionSegundoFactor iniciarActivacion,
      ConfirmarActivacionSegundoFactor confirmarActivacion,
      VerificarSegundoFactor verificar,
      ConsultarSegundoFactor consultar,
      RegenerarCodigosDeRespaldo regenerarCodigos,
      DesactivarSegundoFactor desactivar,
      PlatformTransactionManager transactionManager) {
    this.iniciarActivacion = Objects.requireNonNull(iniciarActivacion);
    this.confirmarActivacion = Objects.requireNonNull(confirmarActivacion);
    this.verificar = Objects.requireNonNull(verificar);
    this.consultar = Objects.requireNonNull(consultar);
    this.regenerarCodigos = Objects.requireNonNull(regenerarCodigos);
    this.desactivar = Objects.requireNonNull(desactivar);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  /** Genera una clave nueva y la deja pendiente hasta que se confirme con el primer código. */
  @PostMapping("/activacion")
  public ActivacionSegundoFactorRespuesta iniciarActivacionSegundoFactor(
      @RequestBody IniciarActivacionSegundoFactorRequest cuerpo) {
    UUID usuarioId = usuarioAutenticadoId();
    ActivacionIniciada activacion =
        transaccion.execute(
            estado ->
                iniciarActivacion.ejecutar(
                    new IniciarActivacionSegundoFactorComando(usuarioId, cuerpo.claveActual())));
    return new ActivacionSegundoFactorRespuesta(
        activacion.claveBase32(),
        activacion.uri(),
        activacion.digitos(),
        activacion.periodoSegundos());
  }

  /**
   * Activa el factor con el primer código de la aplicación. Revoca las demás sesiones y abre una
   * nueva: responde como el inicio de sesión, con cookie, y además con los códigos de respaldo.
   */
  @PostMapping("/activacion/confirmar")
  public ResponseEntity<ActivacionConfirmadaRespuesta> confirmarActivacionSegundoFactor(
      @RequestBody CodigoSegundoFactorRequest cuerpo) {
    UUID usuarioId = usuarioAutenticadoId();
    ActivacionConfirmada confirmada =
        transaccion.execute(
            estado ->
                confirmarActivacion.ejecutar(
                    new ConfirmarActivacionSegundoFactorComando(usuarioId, cuerpo.codigo())));
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, SesionConCookie.cookieDe(confirmada.sesion()).toString())
        .body(
            new ActivacionConfirmadaRespuesta(
                SesionConCookie.cuerpo(confirmada.sesion()), confirmada.codigosDeRespaldo()));
  }

  /**
   * El segundo paso del inicio de sesión: el desafío del 202 y el código abren la sesión, con la
   * misma respuesta y la misma cookie que el inicio de sesión sin factor. Es la única ruta de este
   * controlador que no pide sesión —quien llega aquí todavía no la tiene—, y por eso la abre {@code
   * ConfiguracionSeguridad} y la frena también el filtro por IP de {@code /auth}.
   */
  @PostMapping("/verificacion")
  public ResponseEntity<SesionRespuesta> verificarSegundoFactor(
      @RequestBody VerificarSegundoFactorRequest cuerpo, HttpServletRequest peticion) {
    String origen = CuboDeIp.de(IpDelCliente.de(peticion));
    TokensDeSesion tokens =
        transaccion.execute(
            estado ->
                verificar.ejecutar(
                    new VerificarSegundoFactorComando(cuerpo.desafio(), cuerpo.codigo(), origen)));
    return SesionConCookie.respuesta(tokens);
  }

  /** El estado del factor de quien pregunta, para la pantalla de seguridad del panel. */
  @GetMapping
  public EstadoSegundoFactorRespuesta consultarSegundoFactor() {
    UUID usuarioId = usuarioAutenticadoId();
    ConsultarSegundoFactor.Estado estado = transaccion.execute(e -> consultar.ejecutar(usuarioId));
    return new EstadoSegundoFactorRespuesta(
        estado.activo(),
        estado.activadoEn(),
        estado.codigosDeRespaldoRestantes(),
        estado.obligatorio());
  }

  /** Diez códigos nuevos; revoca las demás sesiones y responde con cookie nueva. */
  @PostMapping("/codigos-de-respaldo")
  public ResponseEntity<CodigosDeRespaldoRegeneradosRespuesta> regenerarCodigosDeRespaldo(
      @RequestBody ClaveYCodigoSegundoFactorRequest cuerpo) {
    UUID usuarioId = usuarioAutenticadoId();
    RegenerarCodigosDeRespaldo.CodigosRegenerados regenerados =
        transaccion.execute(
            e ->
                regenerarCodigos.ejecutar(
                    new ClaveYCodigoSegundoFactorComando(
                        usuarioId, cuerpo.claveActual(), cuerpo.codigo())));
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, SesionConCookie.cookieDe(regenerados.sesion()).toString())
        .body(
            new CodigosDeRespaldoRegeneradosRespuesta(
                SesionConCookie.cuerpo(regenerados.sesion()), regenerados.codigosDeRespaldo()));
  }

  /** Quita el factor; revoca las demás sesiones y responde como el inicio de sesión. */
  @PostMapping("/desactivacion")
  public ResponseEntity<SesionRespuesta> desactivarSegundoFactor(
      @RequestBody ClaveYCodigoSegundoFactorRequest cuerpo) {
    UUID usuarioId = usuarioAutenticadoId();
    TokensDeSesion tokens =
        transaccion.execute(
            e ->
                desactivar.ejecutar(
                    new ClaveYCodigoSegundoFactorComando(
                        usuarioId, cuerpo.claveActual(), cuerpo.codigo())));
    return SesionConCookie.respuesta(tokens);
  }

  /** Ver {@code AutenticacionControlador.usuarioAutenticadoId}. */
  static UUID usuarioAutenticadoId() {
    Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
    if (autenticacion == null || !(autenticacion.getPrincipal() instanceof UUID usuarioId)) {
      throw new SesionDeRefrescoInvalidaException();
    }
    return usuarioId;
  }
}
