import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
import {
  esCodigoDeLaAplicacion,
  esCodigoDeLaAplicacionODeRespaldo,
} from '../domain/codigo-segundo-factor';

/**
 * Un campo vacío no es asunto de estos validadores: lo dice `Validators.required`, con su propio
 * mensaje. Así "escribe el código" y "ese código no tiene la forma" no se pisan.
 */
function validadorDe(esValido: (valor: string) => boolean): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const valor = (control.value as string | null) ?? '';
    return valor.trim() === '' || esValido(valor) ? null : { codigoInvalido: true };
  };
}

/** Confirmar la activación pide la aplicación: un código de respaldo todavía no existe. */
export const codigoDeLaAplicacion = validadorDe(esCodigoDeLaAplicacion);

/** Entrar, regenerar y desactivar aceptan también un código de respaldo. */
export const codigoDeLaAplicacionODeRespaldo = validadorDe(esCodigoDeLaAplicacionODeRespaldo);
