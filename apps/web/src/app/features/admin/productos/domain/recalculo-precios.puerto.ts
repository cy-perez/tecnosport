import { InjectionToken } from '@angular/core';
import { PrecioPorRecalcular } from './producto-admin.model';

/**
 * El recálculo de los precios publicados cuando cambia la tarifa de Wompi (ADR-0095). Es una tarea
 * explícita y no algo que pase solo: un precio anunciado no cambia sin que una persona lo vea.
 * Aparte de `RepositorioProductosAdmin` por lo mismo que los no publicados: es una sola pantalla, y
 * aquel tiene varios dobles de prueba que tendrían que aprender dos métodos que no usan.
 */
export interface RepositorioRecalculoPrecios {
  /** Las variantes cuyo publicado no corresponde a la tarifa de hoy; vacía si todo está al día. */
  previsualizar(): Promise<readonly PrecioPorRecalcular[]>;
  /** Recalcula todas las desactualizadas y devuelve cuántas cambió. */
  aplicar(): Promise<number>;
}

export const REPOSITORIO_RECALCULO_PRECIOS = new InjectionToken<RepositorioRecalculoPrecios>(
  'RepositorioRecalculoPrecios',
);
