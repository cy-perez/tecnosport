package co.tecnosport.api.application.proveedores;

/** Lo que costó una llamada: modelo, tokens y latencia. Va al registro, nunca el texto. */
public record UsoDelExtractor(
    String modelo, long tokensDeEntrada, long tokensDeSalida, long latenciaMilis) {}
