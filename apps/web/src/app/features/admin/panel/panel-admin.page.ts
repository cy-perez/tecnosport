import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { TsBoton } from '../../../shared/ui/boton/ts-boton';
import { SesionStore } from '../../../core/autenticacion/sesion.store';
import { usarVariantesSinMedir } from '../productos/application/listar-variantes-sin-medir.consulta';

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
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PanelAdminPage {
  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);
  protected readonly sesionStore = inject(SesionStore);

  /**
   * El vigilante de lo que falta por medir.
   *
   * <p>Vive en el panel y no en la lista de productos porque el panel es la pantalla por la que se
   * pasa siempre, y este aviso existe justo para lo que nadie va a buscar. Una variante sin medir
   * se vende —solo con recogida en el punto, `ADR-0046`—, así que nada falla y nadie se entera:
   * "temporal" se vuelve permanente por olvido. Hasta hoy el único modo de saber cuántas había era
   * consultar la base.
   *
   * <p>El enlace aparece solo cuando hay algo que medir, que es lo que pide la regla de navegación
   * del plan para una pantalla que depende de un estado. Con la cuenta en cero no hay aviso ni
   * enlace, y eso es correcto: no hay nada que hacer allá.
   */
  private readonly sinMedir = usarVariantesSinMedir();

  protected readonly totalSinMedir = computed(() => this.sinMedir.data()?.total ?? 0);
  protected readonly sinMedirEnPublicados = computed(
    () => this.sinMedir.data()?.totalEnPublicados ?? 0,
  );
  protected readonly haySinMedir = computed(() => this.totalSinMedir() > 0);

  protected async cerrarSesion(): Promise<void> {
    await this.sesionStore.cerrarSesion();
    const idioma = this.transloco.activeLang();
    void this.router.navigate(['/' + idioma, 'admin', 'iniciar-sesion']);
  }
}
