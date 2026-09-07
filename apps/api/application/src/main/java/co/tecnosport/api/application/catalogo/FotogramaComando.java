package co.tecnosport.api.application.catalogo;

/**
 * Lo que el asistente reporta de cada fotograma que subió. {@code ancho}, {@code alto} y {@code
 * hash} los declara el cliente, igual que en la imagen principal (ADR-0016): el backend los
 * contrasta contra lo que exige un set de rotación, pero no los mide contra los bytes reales — que
 * nunca pasan por aquí, esa es la razón de ser de la subida directa.
 */
public record FotogramaComando(int orden, String objectKey, int ancho, int alto, String hash) {}
