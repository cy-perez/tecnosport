import { afterNextRender, ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { urlEnOtroIdioma } from '../../core/idioma/idioma.servicio';

type Tema = 'claro' | 'oscuro' | 'sistema';

function leerCookie(nombre: string): string | undefined {
  return document.cookie
    .split('; ')
    .find((fragmento) => fragmento.startsWith(`${nombre}=`))
    ?.split('=')[1];
}

@Component({
  selector: 'app-encabezado',
  imports: [TranslocoPipe],
  templateUrl: './encabezado.html',
  styleUrl: './encabezado.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Encabezado {
  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);

  protected readonly idiomas = ['es', 'en'] as const;
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

  // Navega a la misma ruta con el otro prefijo — nunca a la portada
  // (docs/05-i18n.md).
  protected cambiarIdioma(idioma: string): void {
    this.router.navigateByUrl(urlEnOtroIdioma(this.router.url, idioma));
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
