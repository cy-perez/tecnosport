import { afterNextRender, ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { SesionStore } from '../../core/autenticacion/sesion.store';
import { CarritoStore } from '../../features/carrito/application/carrito.store';
import { TsSelectorIdioma } from '../../shared/ts-selector-idioma/ts-selector-idioma';

type Tema = 'claro' | 'oscuro' | 'sistema';

function leerCookie(nombre: string): string | undefined {
  return document.cookie
    .split('; ')
    .find((fragmento) => fragmento.startsWith(`${nombre}=`))
    ?.split('=')[1];
}

@Component({
  selector: 'app-encabezado',
  imports: [TranslocoPipe, RouterLink, TsSelectorIdioma],
  templateUrl: './encabezado.html',
  styleUrl: './encabezado.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Encabezado {
  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);
  protected readonly carrito = inject(CarritoStore);
  protected readonly sesion = inject(SesionStore);

  protected readonly idiomaActual = this.transloco.activeLang;
  protected readonly temaElegido = signal<Tema>('sistema');

  constructor() {
    afterNextRender(() => {
      const cookie = leerCookie('ts-tema');
      if (cookie === 'claro' || cookie === 'oscuro' || cookie === 'sistema') {
        this.temaElegido.set(cookie);
      }
    });
  }

  // Un fallo de red al cerrar sesión no puede dejar al visitante atrapado en
  // el encabezado: se navega igual. El access token vive solo en memoria, así
  // que recargar ya lo deja sin sesión.
  protected async cerrarSesion(): Promise<void> {
    await this.sesion.cerrarSesion().catch(() => undefined);
    void this.router.navigate(['/', this.idiomaActual()]);
  }

  protected cambiarTema(tema: Tema): void {
    document.cookie = `ts-tema=${tema}; path=/; max-age=31536000; samesite=lax`;
    const visual =
      tema === 'sistema'
        ? window.matchMedia('(prefers-color-scheme: dark)').matches
          ? 'oscuro'
          : 'claro'
        : tema;
    document.documentElement.setAttribute('data-tema', visual);
    this.temaElegido.set(tema);
  }
}
