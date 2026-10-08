import { DOCUMENT, inject, Injectable } from '@angular/core';
import { BotonGoogle, OpcionesBotonGoogle } from '../domain/boton-google.puerto';

/**
 * Lo poco de Google Identity Services que se usa: `initialize` con el cliente y la función que
 * recibe la credencial, y `renderButton` para pintar el botón oficial. Escrito aquí a mano en vez
 * de traer los tipos de Google como dependencia.
 */
interface IdentidadGoogleGlobal {
  accounts: {
    id: {
      initialize(config: {
        client_id: string;
        callback: (respuesta: { credential: string }) => void;
        ux_mode?: 'popup' | 'redirect';
      }): void;
      renderButton(
        contenedor: HTMLElement,
        opciones: {
          type: 'standard';
          theme: 'outline' | 'filled_black';
          size: 'large';
          text: 'signin_with' | 'signup_with';
          shape: 'rectangular';
          locale: string;
          width?: number;
        },
      ): void;
    };
  };
}

/**
 * El botón de Google sobre su script oficial (ADR-0074). El script se carga una sola vez y solo
 * cuando una pantalla lo necesita: no viaja con el resto del sitio, que no lo usa.
 */
@Injectable()
export class BotonGoogleGis implements BotonGoogle {
  private readonly documento = inject(DOCUMENT);
  private cargando: Promise<IdentidadGoogleGlobal> | null = null;

  async pintar(contenedor: HTMLElement, opciones: OpcionesBotonGoogle): Promise<void> {
    const google = await this.cargar(opciones.configuracion.urlScript);
    google.accounts.id.initialize({
      client_id: opciones.configuracion.clienteId,
      callback: (respuesta) => opciones.alObtenerCredencial(respuesta.credential),
      ux_mode: 'popup',
    });
    google.accounts.id.renderButton(contenedor, {
      type: 'standard',
      theme: opciones.oscuro ? 'filled_black' : 'outline',
      size: 'large',
      text: opciones.modo === 'registrar' ? 'signup_with' : 'signin_with',
      shape: 'rectangular',
      locale: opciones.idioma,
      // El ancho de la columna del formulario, medido: el botón de Google lo pide en número.
      width: contenedor.clientWidth || undefined,
    });
  }

  private cargar(url: string): Promise<IdentidadGoogleGlobal> {
    const ventana = this.documento.defaultView as
      (Window & { google?: IdentidadGoogleGlobal }) | null;
    if (ventana?.google?.accounts) {
      return Promise.resolve(ventana.google);
    }
    this.cargando ??= new Promise((resolver, rechazar) => {
      const script = this.documento.createElement('script');
      script.src = url;
      script.async = true;
      script.onload = () =>
        ventana?.google ? resolver(ventana.google) : rechazar(new Error('Google no cargó'));
      script.onerror = () => {
        this.cargando = null;
        rechazar(new Error('Google no cargó'));
      };
      this.documento.head.appendChild(script);
    });
    return this.cargando;
  }
}
