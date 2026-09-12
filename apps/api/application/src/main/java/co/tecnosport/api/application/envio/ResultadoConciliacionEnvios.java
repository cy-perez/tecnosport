package co.tecnosport.api.application.envio;

/** Lo que hizo una corrida de la conciliación, para el registro de la tarea programada. */
public record ResultadoConciliacionEnvios(int revisados, int conEventosNuevos, int sinNovedad) {}
