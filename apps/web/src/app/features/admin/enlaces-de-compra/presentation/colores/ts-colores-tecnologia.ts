import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { RouterLink } from '@angular/router';
import { fechaConHora } from '../../../../../core/i18n/fecha-colombia';
import { usarIdiomaActivo, usarTraductor } from '../../../../../core/i18n/traductor';
import { PORTAPAPELES } from '../../../../../core/portapapeles/portapapeles';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { TsCampo } from '../../../../../shared/ui/campo/ts-campo';
import {
  ProductoAdminDetalle,
  VarianteResumenAdmin,
} from '../../../productos/domain/producto-admin.model';
import {
  usarDefinirPaleta,
  usarGenerarEnlaceDeCompra,
} from '../../application/enlaces-de-compra.consultas';
import { EnlaceDeCompraAdmin } from '../../domain/enlace-de-compra-admin.model';
import { compraPorWhatsapp } from '../mensaje-de-compra';

/**
 * Los colores de un equipo de tecnología, en su edición (ADR-0086): la paleta oficial del modelo,
 * y los colores que la lista del proveedor no marcó, con el botón para generar el enlace de compra
 * cuando el proveedor confirma que lo tiene.
 *
 * Vive en su propia funcionalidad y no dentro de `productos` porque lo que hace es otra cosa —
 * confirmar ventas por WhatsApp—, y la edición del producto ya pasa de las mil líneas.
 */
@Component({
  selector: 'ts-colores-tecnologia',
  imports: [ReactiveFormsModule, RouterLink, TranslocoPipe, TsBoton, TsCampo],
  templateUrl: './ts-colores-tecnologia.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsColoresTecnologia {
  readonly producto = input.required<ProductoAdminDetalle>();

  private readonly traducir = usarTraductor();
  protected readonly idioma = usarIdiomaActivo();
  private readonly portapapeles = inject(PORTAPAPELES);
  private readonly mutacionPaleta = usarDefinirPaleta();
  private readonly mutacionGenerar = usarGenerarEnlaceDeCompra();

  /** Un color por línea: es como se pega desde la ficha de la marca. */
  protected readonly paleta = new FormControl('', { nonNullable: true });
  protected readonly guardando = computed(() => this.mutacionPaleta.isPending());
  protected readonly avisoPaleta = signal<string | null>(null);
  protected readonly errorPaleta = signal<string | null>(null);

  /** La variante que se está generando, para el anillo de su botón y la guarda de reentrada. */
  protected readonly generando = signal<string | null>(null);
  protected readonly generado = signal<EnlaceDeCompraAdmin | null>(null);
  protected readonly errorGenerar = signal<string | null>(null);
  protected readonly avisoCopia = signal<string | null>(null);

  protected readonly porConfirmar = computed(() =>
    this.producto().variantes.filter((variante) => variante.colorPorConfirmar === true),
  );

  protected readonly compra = computed(() => {
    const enlace = this.generado();
    return enlace ? compraPorWhatsapp(enlace, this.idioma(), this.traducir()) : null;
  });

  protected readonly vigenteHasta = computed(() => {
    const enlace = this.generado();
    return enlace ? fechaConHora(enlace.expiraEn, this.idioma()) : '';
  });

  constructor() {
    // La paleta guardada entra al campo al cargar otro producto, no cada vez que vuelve del
    // servidor: lo que se está escribiendo no se pisa con una revalidación.
    const id = computed(() => this.producto().id);
    effect(() => {
      id();
      const producto = untracked(() => this.producto());
      this.paleta.setValue((producto.paleta ?? []).join('\n'));
    });
  }

  /** «256 · Morado»: lo que la distingue, por el nombre del atributo, como el pedido. */
  protected detalle(variante: VarianteResumenAdmin): string {
    return [...variante.atributos]
      .sort((uno, otro) => uno.nombre.localeCompare(otro.nombre))
      .map((atributo) => atributo.valor)
      .join(' · ');
  }

  protected guardarPaleta(): void {
    if (this.guardando()) {
      return;
    }
    this.avisoPaleta.set(null);
    this.errorPaleta.set(null);
    const colores = this.paleta.value
      .split('\n')
      .map((color) => color.trim())
      .filter((color) => color.length > 0);
    this.mutacionPaleta.mutate(
      { productoId: this.producto().id, colores },
      {
        onSuccess: (nuevas) =>
          this.avisoPaleta.set(
            this.traducir()('admin.enlacesCompra.seccion.paletaGuardada', { nuevas }),
          ),
        onError: () =>
          this.errorPaleta.set(this.traducir()('admin.enlacesCompra.seccion.errorPaleta')),
      },
    );
  }

  protected generar(variante: VarianteResumenAdmin): void {
    if (this.generando() !== null) {
      return;
    }
    this.generando.set(variante.id);
    this.generado.set(null);
    this.errorGenerar.set(null);
    this.avisoCopia.set(null);
    this.mutacionGenerar.mutate(variante.id, {
      onSuccess: (enlace) => this.generado.set(enlace),
      onError: () =>
        this.errorGenerar.set(this.traducir()('admin.enlacesCompra.seccion.errorGenerar')),
      onSettled: () => this.generando.set(null),
    });
  }

  protected copiar(texto: string, url: string): void {
    this.avisoCopia.set(null);
    this.portapapeles.copiar(texto).then(
      () => this.avisoCopia.set(this.traducir()('admin.enlacesCompra.bandeja.copiado')),
      () =>
        this.avisoCopia.set(this.traducir()('admin.enlacesCompra.bandeja.errorCopiar', { url })),
    );
  }
}
