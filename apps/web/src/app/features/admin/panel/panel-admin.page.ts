import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { TsBoton } from '../../../shared/ts-boton/ts-boton';
import { SesionStore } from '../../../core/autenticacion/sesion.store';

/**
 * Placeholder: solo confirma que la sesión y `adminGuard` funcionan. El
 * contenido real del panel (productos, variantes, inventario, imágenes,
 * pantallas de pedidos) es trabajo de sesiones futuras — ver el mapa de
 * la Fase 4 en `docs/09-plan-de-arranque.md`.
 */
@Component({
  selector: 'app-panel-admin',
  imports: [TranslocoPipe, TsBoton, RouterLink],
  templateUrl: './panel-admin.page.html',
  styleUrl: './panel-admin.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PanelAdminPage {
  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);
  protected readonly sesionStore = inject(SesionStore);

  protected async cerrarSesion(): Promise<void> {
    await this.sesionStore.cerrarSesion();
    const idioma = this.transloco.activeLang();
    void this.router.navigate(['/' + idioma, 'admin', 'iniciar-sesion']);
  }
}
