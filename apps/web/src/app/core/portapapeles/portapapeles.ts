import { DOCUMENT, InjectionToken, inject } from '@angular/core';

/**
 * Copiar un texto para pegarlo en otro lado —el enlace de compra en el chat de WhatsApp—. Detrás
 * de un puerto, como toda API del navegador (`docs/01-arquitectura.md`): jsdom no tiene
 * `navigator.clipboard`, y una prueba tiene que poder decir qué se copió.
 */
export interface Portapapeles {
  /** Rechaza si el navegador no lo permite: sin HTTPS, o sin el foco en la página. */
  copiar(texto: string): Promise<void>;
}

export const PORTAPAPELES = new InjectionToken<Portapapeles>('Portapapeles', {
  providedIn: 'root',
  factory: () => {
    const ventana = inject(DOCUMENT).defaultView;
    return {
      copiar: (texto: string) =>
        ventana?.navigator.clipboard
          ? ventana.navigator.clipboard.writeText(texto)
          : Promise.reject(new Error('El navegador no ofrece portapapeles.')),
    };
  },
});
