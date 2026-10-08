import { InjectionToken } from '@angular/core';

/** Lo que el sitio sabe de "Iniciar sesión con Google" en este ambiente (ADR-0074). */
export interface ConfiguracionGoogle {
  readonly habilitado: boolean;
  /** El identificador público del cliente OAuth. No es un secreto: va en la página. */
  readonly clienteId: string;
  /** De dónde se carga el script del botón. Configuración del servidor, no del código (regla 5). */
  readonly urlScript: string;
}

export interface OpcionesBotonGoogle {
  readonly configuracion: ConfiguracionGoogle;
  /** El texto del botón lo pone Google en el idioma pedido: "Iniciar sesión con…" o "Registrarse…". */
  readonly modo: 'iniciar' | 'registrar';
  readonly idioma: string;
  readonly oscuro: boolean;
  readonly alObtenerCredencial: (credencial: string) => void;
}

/**
 * Pinta el botón oficial de Google dentro de un contenedor. Es oficial a propósito: Google exige su
 * propio botón para usar su marca, y su script es el que abre la ventana y entrega la credencial.
 * Detrás de un puerto porque ese script es un tercero que no existe en el servidor ni en las
 * pruebas.
 */
export interface BotonGoogle {
  pintar(contenedor: HTMLElement, opciones: OpcionesBotonGoogle): Promise<void>;
}

export const BOTON_GOOGLE = new InjectionToken<BotonGoogle>('BotonGoogle');
