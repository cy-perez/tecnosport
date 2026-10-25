import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { fechaConHora } from '../../../../../core/i18n/fecha-colombia';
import { usarIdiomaActivo, usarTraductor } from '../../../../../core/i18n/traductor';
import { PORTAPAPELES } from '../../../../../core/portapapeles/portapapeles';
import { usarFoco } from '../../../../../shared/foco/foco';
import { TsEsqueleto } from '../../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsMigas } from '../../../../../shared/ts-migas/ts-migas';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { usarMigasAdmin } from '../../../migas-admin';
import {
  usarAnularEnlaceDeCompra,
  usarEnlacesDeCompra,
} from '../../application/enlaces-de-compra.consultas';
import { EnlaceDeCompraAdmin } from '../../domain/enlace-de-compra-admin.model';
import { compraPorWhatsapp } from '../mensaje-de-compra';

/**
 * Los enlaces de compra de la última semana (ADR-0086): cuáles siguen vigentes, cuáles terminaron
 * en un pedido. Desde aquí se vuelven a copiar —el mensaje se perdió en el chat— o se anulan, y la
 * unidad apartada queda libre. Se generan desde la edición del producto, donde están los colores.
 */
@Component({
  selector: 'app-enlaces-de-compra-admin',
  imports: [TranslocoPipe, TsBoton, TsEsqueleto, TsMigas],
  templateUrl: './enlaces-de-compra-admin.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EnlacesDeCompraAdminPage {
  protected readonly migas = usarMigasAdmin([{ clave: 'admin.enlacesCompra.bandeja.titulo' }]);

  private readonly traducir = usarTraductor();
  protected readonly idioma = usarIdiomaActivo();
  private readonly portapapeles = inject(PORTAPAPELES);

  protected readonly consulta = usarEnlacesDeCompra();
  private readonly mutacionAnular = usarAnularEnlaceDeCompra();

  protected readonly enlaces = computed<readonly EnlaceDeCompraAdmin[]>(
    () => this.consulta.data() ?? [],
  );
  /** El que se está anulando: el anillo de su botón y la guarda de reentrada. */
  protected readonly anulando = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  /** Anular quita los botones de la fila: el foco va al aviso y no se pierde en `<body>`. */
  private readonly enfocar = usarFoco();
  private readonly avisoRef = viewChild<ElementRef<HTMLElement>>('avisoRef');

  protected vence(enlace: EnlaceDeCompraAdmin): string {
    return fechaConHora(enlace.expiraEn, this.idioma());
  }

  protected copiar(enlace: EnlaceDeCompraAdmin, que: 'url' | 'mensaje'): void {
    const compra = compraPorWhatsapp(enlace, this.idioma(), this.traducir());
    if (!compra) {
      return;
    }
    this.aviso.set(null);
    this.error.set(null);
    this.portapapeles.copiar(que === 'url' ? compra.url : compra.mensaje).then(
      () => this.aviso.set(this.traducir()('admin.enlacesCompra.bandeja.copiado')),
      () =>
        this.error.set(
          this.traducir()('admin.enlacesCompra.bandeja.errorCopiar', { url: compra.url }),
        ),
    );
  }

  protected anular(enlace: EnlaceDeCompraAdmin): void {
    if (this.anulando() !== null) {
      return;
    }
    this.anulando.set(enlace.id);
    this.aviso.set(null);
    this.error.set(null);
    this.mutacionAnular.mutate(enlace.id, {
      onSuccess: () => {
        this.aviso.set(this.traducir()('admin.enlacesCompra.bandeja.anulado'));
        this.enfocar(() => this.avisoRef()?.nativeElement);
      },
      onError: () => this.error.set(this.traducir()('admin.enlacesCompra.bandeja.errorAnular')),
      onSettled: () => this.anulando.set(null),
    });
  }
}
