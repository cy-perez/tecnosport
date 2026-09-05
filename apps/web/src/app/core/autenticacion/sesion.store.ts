import { isPlatformBrowser } from '@angular/common';
import { afterNextRender, computed, inject, Injectable, PLATFORM_ID, signal } from '@angular/core';
import { REPOSITORIO_SESION } from './repositorio-sesion.puerto';
import { Sesion } from './sesion.model';

/**
 * Único en toda la app (`providedIn: 'root'`), mismo motivo que
 * `CarritoStore`/`CheckoutStore`: el encabezado, el guardia de rutas de
 * `admin` y cualquier página que necesite saber si hay sesión comparten la
 * misma señal.
 *
 * El access token vive SOLO en memoria (`docs/08-seguridad-legal.md`) —
 * nunca `localStorage`. Por eso, al cargar la app en el navegador, se
 * intenta un refresco silencioso una vez contra la cookie `HttpOnly` de
 * refresco (que sí sobrevive un F5): "el token no va en `localStorage`" no
 * es lo mismo que "la sesión no sobrevive un refresh".
 *
 * En SSR no hay refresco silencioso — leer una cookie `HttpOnly` desde el
 * servidor exigiría reenviarla a mano en cada petición, y `admin` no
 * necesita SEO ni primer pintado rápido (está detrás de sesión). Se
 * resuelve "sin sesión" de inmediato, determinista, mismo criterio que
 * `CarritoStore` en SSR (`apps/web/CLAUDE.md`, `ADR-0011`): el servidor
 * siempre sirve "sin sesión" y el cliente la carga de verdad tras hidratar.
 */
@Injectable({ providedIn: 'root' })
export class SesionStore {
  private readonly repositorio = inject(REPOSITORIO_SESION);
  private readonly esNavegador = isPlatformBrowser(inject(PLATFORM_ID));

  readonly sesion = signal<Sesion | null>(null);
  readonly esAdmin = computed(() => this.sesion()?.rol === 'ADMIN');

  private resolverListo!: () => void;
  /** Se resuelve cuando termina el intento de refresco silencioso del
   * arranque (o de inmediato en SSR). El guardia de rutas admin espera
   * esto antes de decidir, para no redirigir a quien sí tiene una sesión
   * válida por cookie. */
  readonly listo = new Promise<void>((resolve) => {
    this.resolverListo = resolve;
  });

  constructor() {
    if (!this.esNavegador) {
      this.resolverListo();
      return;
    }
    afterNextRender(() => {
      // Best-effort: un refresco que falla en el arranque (red caída,
      // backend caído) deja "sin sesión" en vez de tumbar la app — el
      // usuario simplemente ve el login, como si no hubiera cookie.
      void this.intentarRefrescar()
        .catch(() => this.sesion.set(null))
        .finally(() => this.resolverListo());
    });
  }

  async iniciarSesion(correo: string, clave: string): Promise<Sesion> {
    const sesion = await this.repositorio.iniciarSesion(correo, clave);
    this.sesion.set(sesion);
    return sesion;
  }

  async cerrarSesion(): Promise<void> {
    await this.repositorio.cerrarSesion();
    this.sesion.set(null);
  }

  async intentarRefrescar(): Promise<void> {
    const sesion = await this.repositorio.refrescar();
    this.sesion.set(sesion);
  }
}
