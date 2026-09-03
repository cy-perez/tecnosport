package co.tecnosport.api.application.usuario;

/**
 * Puerto hacia el algoritmo de hash de contraseñas (docs/08-seguridad-legal.md: Argon2id, o BCrypt
 * costo 12 si Argon2 complica el despliegue). El dominio no sabe cuál de los dos es — {@link
 * co.tecnosport.api.domain.usuario.Usuario#claveHash()} es opaco a propósito.
 */
public interface CodificadorDeClaves {

  String codificar(String claveTextoPlano);

  boolean verificar(String claveTextoPlano, String claveHash);
}
