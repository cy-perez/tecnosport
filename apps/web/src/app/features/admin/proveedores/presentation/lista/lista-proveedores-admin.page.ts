import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { usarTraductor } from '../../../../../core/i18n/traductor';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { TsEsqueleto } from '../../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsMigas } from '../../../../../shared/ts-migas/ts-migas';
import { usarMigasAdmin } from '../../../migas-admin';
import { usarProveedoresAdmin } from '../../application/listar-proveedores.consulta';
import { LineaProveedor, Proveedor } from '../../domain/proveedor.model';

const CLAVE_LINEA: Record<LineaProveedor, string> = {
  BOLSOS: 'admin.proveedores.lineas.BOLSOS',
  ROPA: 'admin.proveedores.lineas.ROPA',
  TECNOLOGIA: 'admin.proveedores.lineas.TECNOLOGIA',
};

/** Contorno y no relleno, como la insignia de estado de la lista de productos. */
const CLASES_INSIGNIA =
  'inline-flex items-center rounded-completo border px-12 py-4 text-xs font-medio';

/**
 * Los proveedores de WhatsApp y el enlace para dar de alta uno.
 *
 * Eliminar y desactivar se hacen desde su ficha: la lista solo enlaza a ella.
 */
@Component({
  selector: 'app-lista-proveedores-admin',
  imports: [TranslocoPipe, TsBoton, TsEsqueleto, TsMigas],
  templateUrl: './lista-proveedores-admin.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ListaProveedoresAdminPage {
  protected readonly migas = usarMigasAdmin([{ clave: 'admin.proveedores.titulo' }]);
  private readonly traducir = usarTraductor();
  private readonly transloco = inject(TranslocoService);

  protected readonly consulta = usarProveedoresAdmin();
  protected readonly proveedores = computed<readonly Proveedor[]>(() => this.consulta.data() ?? []);

  protected etiquetaLinea(linea: LineaProveedor): string {
    return this.traducir()(CLAVE_LINEA[linea]);
  }

  /** «×1,35» en español y «×1.35» en inglés: el separador decimal es del idioma. */
  protected margen(proveedor: Proveedor): string {
    const idioma = this.transloco.activeLang();
    const factor = new Intl.NumberFormat(idioma === 'en' ? 'en-US' : 'es-CO', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(proveedor.factorDeMargen);
    return this.traducir()('admin.proveedores.columnas.margenValor', { factor });
  }

  protected clasesActivo(activo: boolean): string {
    return (
      CLASES_INSIGNIA +
      (activo ? ' border-ts-exito text-ts-exito' : ' border-ts-borde text-ts-texto-suave')
    );
  }
}
