import { Provider } from '@angular/core';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import {
  BOTON_GOOGLE,
  BotonGoogle,
  ConfiguracionGoogle,
  OpcionesBotonGoogle,
} from '../app/features/cuenta/domain/boton-google.puerto';

/**
 * El botón de Google sin Google (ADR-0074): no carga ningún script y deja que la prueba entregue la
 * credencial cuando quiera, como si la persona hubiera elegido su cuenta en la ventana de Google.
 */
export class BotonGoogleFalso implements BotonGoogle {
  private alObtener: ((credencial: string) => void) | null = null;
  pintado = false;

  async pintar(_contenedor: HTMLElement, opciones: OpcionesBotonGoogle): Promise<void> {
    this.pintado = true;
    this.alObtener = opciones.alObtenerCredencial;
  }

  entregar(credencial: string): void {
    if (!this.alObtener) {
      throw new Error('el botón de Google no se pintó');
    }
    this.alObtener(credencial);
  }
}

export const GOOGLE_HABILITADO: ConfiguracionGoogle = {
  habilitado: true,
  clienteId: 'cliente.apps.googleusercontent.com',
  urlScript: 'https://script.de.prueba/gsi',
};

/** Lo que una pantalla con el botón de Google necesita además de lo suyo. */
export function proveedoresDeGoogle(boton: BotonGoogle = new BotonGoogleFalso()): Provider[] {
  return [
    { provide: BOTON_GOOGLE, useValue: boton },
    provideTanStackQuery(new QueryClient({ defaultOptions: { queries: { retry: false } } })),
  ];
}
