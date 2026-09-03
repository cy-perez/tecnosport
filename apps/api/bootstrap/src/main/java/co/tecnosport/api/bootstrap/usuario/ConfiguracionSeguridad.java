package co.tecnosport.api.bootstrap.usuario;

import co.tecnosport.api.presentation.usuario.FiltroAutenticacionJwt;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 */
@Configuration
@EnableWebSecurity
public class ConfiguracionSeguridad {

  @Bean
  public SecurityFilterChain cadenaDeSeguridad(
      HttpSecurity http, FiltroAutenticacionJwt filtroAutenticacionJwt) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/v1/admin/**").hasRole("ADMIN").anyRequest().permitAll())
        .addFilterBefore(filtroAutenticacionJwt, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}
