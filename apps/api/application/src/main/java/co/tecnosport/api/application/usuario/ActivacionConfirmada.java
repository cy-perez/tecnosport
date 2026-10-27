package co.tecnosport.api.application.usuario;

import java.util.List;

/**
 * La sesión nueva y los diez códigos de respaldo, en claro y por única vez, ya en la forma en que
 * se muestran ({@code ABCD-EFGH-IJKL-MNOP}).
 */
public record ActivacionConfirmada(TokensDeSesion sesion, List<String> codigosDeRespaldo) {}
