import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { SesionStore } from '../../core/autenticacion/sesion.store';
import { CarritoStore } from '../../features/carrito/application/carrito.store';
import { TsSelectorIdioma } from '../../shared/ts-selector-idioma/ts-selector-idioma';
import { TsSelectorTema } from '../../shared/ts-selector-tema/ts-selector-tema';

@Component({
  selector: 'app-encabezado',
  imports: [TranslocoPipe, RouterLink, TsSelectorIdioma, TsSelectorTema],
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

  // Un fallo de red al cerrar sesión no puede dejar al visitante atrapado en
  // el encabezado: se navega igual. El access token vive solo en memoria, así
  // que recargar ya lo deja sin sesión.
  protected async cerrarSesion(): Promise<void> {
    await this.sesion.cerrarSesion().catch(() => undefined);
    void this.router.navigate(['/', this.idiomaActual()]);
  }
}
