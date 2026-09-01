import { Component, afterNextRender, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

type Tema = 'claro' | 'oscuro' | 'sistema';

function leerCookie(nombre: string): string | undefined {
  return document.cookie
    .split('; ')
    .find((fragmento) => fragmento.startsWith(`${nombre}=`))
    ?.split('=')[1];
}

@Component({
  imports: [RouterOutlet, TranslocoPipe],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  private readonly transloco = inject(TranslocoService);

  protected readonly idiomas = ['es', 'en'] as const;
  protected readonly idiomaActual = signal(this.transloco.getActiveLang());
  protected readonly temaElegido = signal<Tema>('sistema');

  constructor() {
    afterNextRender(() => {
      const cookie = leerCookie('ts-tema');
      if (cookie === 'claro' || cookie === 'oscuro' || cookie === 'sistema') {
        this.temaElegido.set(cookie);
      }
    });
  }

  protected cambiarIdioma(idioma: string): void {
    this.transloco.setActiveLang(idioma);
    this.idiomaActual.set(idioma);
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
