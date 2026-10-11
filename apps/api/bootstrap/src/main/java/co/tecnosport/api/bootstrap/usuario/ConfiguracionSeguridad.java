package co.tecnosport.api.bootstrap.usuario;

import co.tecnosport.api.application.proveedores.VerificadorDeTareasDeIngesta;
import co.tecnosport.api.presentation.proveedores.FiltroTareasDeIngesta;
import co.tecnosport.api.presentation.usuario.FiltroAutenticacionJwt;
import co.tecnosport.api.presentation.usuario.PuntoDeEntradaNoAutenticado;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Sin sesión de servidor (docs/08-seguridad-legal.md: autenticación propia con JWT): stateless, sin
 * CSRF de Spring Security — la cookie de refresco ya se protege con {@code SameSite=Lax} ({@code
 * AutenticacionControlador}), la mitigación estándar para una API sin formularios ni sesión de
 * navegador. {@code /api/v1/admin/**} exige rol {@code ADMIN} (sin rutas todavía, listo para cuando
 * existan); el resto es público — cada ruta decide sus propias reglas de negocio (Wompi verifica su
 * propia firma, por ejemplo), esta clase solo decide autenticación.
 *
 * <p>Con una excepción, y es la única de {@code /api/v1/auth}: {@code POST /auth/clave} exige
 * sesión iniciada. El resto de ese prefijo es público por definición —quien registra, verifica,
 * inicia sesión o recupera su clave todavía no tiene una—, pero cambiar la clave desde dentro sí, y
 * el controlador saca de ahí de quién es la clave que cambia. Sin esta línea el endpoint quedaría
 * abierto por el {@code anyRequest().permitAll()} de abajo y llegaría sin principal: un olvido que
 * no falla ruidosamente, solo deja la puerta abierta.
 *
 * <p>Y {@code /api/v1/internal/**} es de Cloud Tasks (ADR-0080): exige el rol que pone {@link
 * FiltroTareasDeIngesta} cuando el token es el de la cuenta de las tareas. Va antes del {@code
 * anyRequest().permitAll()} por la misma razón que la clave: la API de dev es pública.
 */
@Configuration
@EnableWebSecurity
public class ConfiguracionSeguridad {

  @Bean
  public SecurityFilterChain cadenaDeSeguridad(
      HttpSecurity http,
      FiltroAutenticacionJwt filtroAutenticacionJwt,
      VerificadorDeTareasDeIngesta verificadorDeTareas)
      throws Exception {
    http.csrf(csrf -> csrf.disable())
        // Sin esto una peticion sin token recibe 403 -el codigo de "no puedes", para el caso en
        // el que no se sabe quien eres- y el refresco silencioso del frontend, que cuelga del
        // 401, no dispara nunca. Ver PuntoDeEntradaNoAutenticado.
        .exceptionHandling(e -> e.authenticationEntryPoint(new PuntoDeEntradaNoAutenticado()))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/clave")
                    .authenticated()
                    .requestMatchers(FiltroTareasDeIngesta.RUTA + "**")
                    .hasRole(FiltroTareasDeIngesta.ROL)
                    .anyRequest()
                    .permitAll())
        .addFilterBefore(filtroAutenticacionJwt, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(
            new FiltroTareasDeIngesta(verificadorDeTareas),
            UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}
