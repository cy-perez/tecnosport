import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { usarTraductor } from '../../../../../core/i18n/traductor';
import { usarFoco } from '../../../../../shared/foco/foco';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { TsCampo } from '../../../../../shared/ui/campo/ts-campo';
import { TsEsqueleto } from '../../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsMigas } from '../../../../../shared/ts-migas/ts-migas';
import { usarMigasAdmin } from '../../../migas-admin';
import { usarMedidas } from '../../application/listar-medidas.consulta';
import { usarMedirVariante } from '../../application/medir-variante.mutacion';
import { EstadoProducto, MedidaDeVariante } from '../../domain/producto-admin.model';

const CLAVE_ETIQUETA_ESTADO: Record<EstadoProducto, string> = {
  BORRADOR: 'admin.productos.estados.borrador',
  PUBLICADO: 'admin.productos.estados.publicado',
};

/**
 * Las medidas del catálogo: las que faltan y las que ya se tomaron, con el formulario para
 * escribirlas o corregirlas.
 *
 * <p>Existe porque la pantalla de sin-medir suelta una variante justo cuando se mide, y una medida
 * mal tomada cobra el flete equivocado en **cada** pedido de esa variante. `MedirVariante` admite
 * reemplazar desde `ADR-0046` y hasta ahora no había puerta: la única salida era escribir en la
 * base a mano. Pasó de verdad — el JBL Go 5 se cargó con 13x9x6 donde su ficha dice 14x10x6.
 *
 * <p><b>El formulario arranca con las cifras que ya tiene la variante</b>, no en blanco. Corregir
 * un peso mal tecleado es cambiar un número, no volver a medir el paquete entero, y un formulario
 * vacío obliga a copiar tres cifras correctas para tocar la cuarta — que es justo como se
 * equivoca uno.
 */
@Component({
  selector: 'app-medidas-admin',
  imports: [ReactiveFormsModule, TranslocoPipe, TsBoton, TsCampo, TsEsqueleto, TsMigas],
  templateUrl: './medidas-admin.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MedidasAdminPage {
  protected readonly migas = usarMigasAdmin([
    { clave: 'admin.productos.titulo', ruta: ['productos'] },
    { clave: 'admin.productos.medidas.titulo' },
  ]);

  private readonly transloco = inject(TranslocoService);
  private readonly traducir = usarTraductor();

  protected readonly consulta = usarMedidas();
  private readonly mutacion = usarMedirVariante();

  protected readonly variantes = computed<readonly MedidaDeVariante[]>(
    () => this.consulta.data()?.items ?? [],
  );
  protected readonly total = computed(() => this.consulta.data()?.total ?? 0);
  protected readonly totalSinMedir = computed(() => this.consulta.data()?.totalSinMedir ?? 0);

  /** La fila con el formulario abierto. `null` = ninguna. */
  protected readonly midiendo = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  /** Lo que se acaba de guardar, y si reemplazó una medida anterior. */
  protected readonly guardada = signal<{ sku: string; correccion: boolean } | null>(null);

  protected readonly enviando = computed(() => this.mutacion.isPending());

  private readonly enfocarDespuesDePintar = usarFoco();
  private readonly avisoMedidas = viewChild<ElementRef<HTMLElement>>('avisoMedidas');
  private readonly raiz = inject<ElementRef<HTMLElement>>(ElementRef);

  /** El `<button>` real dentro del `ts-boton` que abre el formulario de una fila. */
  private botonDeAbrir(varianteId: string): HTMLElement | null {
    return (
      this.raiz.nativeElement.querySelector<HTMLElement>(`[data-abrir="${varianteId}"] button`) ??
      null
    );
  }

  protected readonly form = new FormGroup({
    pesoGramos: new FormControl<number | null>(null, {
      validators: [Validators.required, Validators.min(1)],
    }),
    largoCm: new FormControl<number | null>(null, {
      validators: [Validators.required, Validators.min(1)],
    }),
    anchoCm: new FormControl<number | null>(null, {
      validators: [Validators.required, Validators.min(1)],
    }),
    altoCm: new FormControl<number | null>(null, {
      validators: [Validators.required, Validators.min(1)],
    }),
  });

  protected etiquetaEstado(estado: EstadoProducto): string {
    return this.traducir()(CLAVE_ETIQUETA_ESTADO[estado]);
  }

  /** El primer campo de las medidas, que es donde tiene que entrar el foco al abrirlas. */
  private primerCampoDe(varianteId: string): HTMLElement | null {
    return this.raiz.nativeElement.querySelector<HTMLElement>(`#medida-peso-${varianteId}`);
  }

  /**
   * Al abrir, el foco entra en la caja. **Faltaba, y sin eso el `(keydown.escape)` del formulario
   * no recibe nunca la tecla**: el foco se queda en el botón que lo abrió, que vive en la fila
   * anterior, así que Escape no cancelaba nada. `editar` sí lo hacía; estas tres copiaron la
   * interacción y solo media vuelta del arreglo —el retorno del foco al cancelar—, que es
   * exactamente lo que `shared/foco/foco.ts` documenta que pasó con este patrón.
   */
  protected abrir(variante: MedidaDeVariante): void {
    this.form.reset({
      pesoGramos: variante.pesoGramos,
      largoCm: variante.largoCm,
      anchoCm: variante.anchoCm,
      altoCm: variante.altoCm,
    });
    this.error.set(null);
    this.guardada.set(null);
    this.midiendo.set(variante.varianteId);
    this.enfocarDespuesDePintar(() => this.primerCampoDe(variante.varianteId));
  }

  /**
   * Cancelar destruye el formulario con el botón "Cancelar" dentro: el foco vuelve a mano al botón
   * que lo abrió, o el navegador lo manda a `<body>`.
   */
  protected cancelar(varianteId: string): void {
    this.midiendo.set(null);
    this.error.set(null);
    this.enfocarDespuesDePintar(() => this.botonDeAbrir(varianteId));
  }

  protected enviar(varianteId: string): void {
    // Guarda de reentrada en vez de `[cargando]`: deshabilitar el botón recién pulsado le quita
    // el foco a quien lo pulsó.
    if (this.enviando()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      // Decir qué falta en vez de solo marcar, y sin deshabilitar el botón: un `<button disabled>`
      // sale del orden de tabulación y quien navega con teclado no llega a enterarse de por qué no
      // pasa nada. Mismo criterio que el resto del panel.
      this.error.set(this.transloco.translate('admin.productos.medidas.faltanCampos'));
      return;
    }
    this.error.set(null);

    const valores = this.form.getRawValue();
    this.mutacion.mutate(
      {
        varianteId,
        pesoGramos: valores.pesoGramos ?? 0,
        largoCm: valores.largoCm ?? 0,
        anchoCm: valores.anchoCm ?? 0,
        altoCm: valores.altoCm ?? 0,
      },
      {
        onSuccess: (resultado) => {
          this.midiendo.set(null);
          // `correccion` lo dice el servidor: es él quien sabe si había un paquete antes, y la
          // diferencia importa — una corrección significa que hubo pedidos cotizados con la
          // medida vieja.
          this.guardada.set({ sku: resultado.sku, correccion: resultado.correccion });
          this.enfocarDespuesDePintar(() => this.avisoMedidas()?.nativeElement);
        },
        onError: () => this.error.set(this.transloco.translate('admin.productos.medidas.error')),
      },
    );
  }
}
