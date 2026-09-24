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
import { usarAjustarExistencia } from '../../application/ajustar-existencia.mutacion';
import { usarExistencias } from '../../application/listar-existencias.consulta';
import {
  EstadoProducto,
  ExistenciaAjustada,
  ExistenciaDeVariante,
} from '../../domain/producto-admin.model';

const CLAVE_ETIQUETA_ESTADO: Record<EstadoProducto, string> = {
  BORRADOR: 'admin.productos.estados.borrador',
  PUBLICADO: 'admin.productos.estados.publicado',
};

/**
 * Las existencias del catálogo y el formulario para contarlas, en la misma pantalla y con el mismo
 * criterio que la de sin-medir: contar es recorrer la bodega con la lista delante, y sacar a quien
 * cuenta de la lista para devolverlo después alarga la única tarea que esta pantalla tiene.
 *
 * <p>Dos cifras por variante —lo que hay y lo que se puede vender—, que son dos cosas distintas:
 * la segunda descuenta las reservas en vuelo. Fueron tres hasta `ADR-0050`, cuando la columna
 * `variante.existencia` desapareció y con ella el descuadre que la tercera servía para marcar.
 */
@Component({
  selector: 'app-existencias-admin',
  imports: [ReactiveFormsModule, TranslocoPipe, TsBoton, TsCampo, TsEsqueleto, TsMigas],
  templateUrl: './existencias-admin.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExistenciasAdminPage {
  protected readonly migas = usarMigasAdmin([
    { clave: 'admin.productos.titulo', ruta: ['productos'] },
    { clave: 'admin.productos.existencias.titulo' },
  ]);

  private readonly transloco = inject(TranslocoService);
  private readonly traducir = usarTraductor();

  protected readonly consulta = usarExistencias();
  private readonly mutacion = usarAjustarExistencia();

  protected readonly variantes = computed<readonly ExistenciaDeVariante[]>(
    () => this.consulta.data()?.items ?? [],
  );
  protected readonly total = computed(() => this.consulta.data()?.total ?? 0);
  protected readonly totalSinExistencia = computed(
    () => this.consulta.data()?.totalSinExistencia ?? 0,
  );

  /** La fila con el formulario abierto. `null` = ninguna. */
  protected readonly contando = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  /** El resultado del último conteo, para poder decir cuál de las tres cosas pasó. */
  protected readonly ajuste = signal<ExistenciaAjustada | null>(null);

  protected readonly enviando = computed(() => this.mutacion.isPending());

  private readonly enfocarDespuesDePintar = usarFoco();
  private readonly avisoExistencias = viewChild<ElementRef<HTMLElement>>('avisoExistencias');
  private readonly raiz = inject<ElementRef<HTMLElement>>(ElementRef);

  /** El `<button>` real dentro del `ts-boton` que abre el conteo de una fila. */
  private botonDeAbrir(varianteId: string): HTMLElement | null {
    return (
      this.raiz.nativeElement.querySelector<HTMLElement>(`[data-abrir="${varianteId}"] button`) ??
      null
    );
  }

  protected readonly form = new FormGroup({
    // Sin valor por omisión: un cero heredado de un formulario en blanco sería un conteo
    // inventado, y este es justo el dato que no se puede inventar. Cero sí es un conteo válido
    // cuando alguien lo escribe — se acabó—, por eso el mínimo es 0 y no 1, al revés que en las
    // medidas del paquete.
    cantidadContada: new FormControl<number | null>(null, {
      validators: [Validators.required, Validators.min(0)],
    }),
    motivo: new FormControl<string>('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
  });

  protected etiquetaEstado(estado: EstadoProducto): string {
    return this.traducir()(CLAVE_ETIQUETA_ESTADO[estado]);
  }

  /** El primer campo del conteo, que es donde tiene que entrar el foco al abrirlo. */
  private primerCampoDe(varianteId: string): HTMLElement | null {
    return this.raiz.nativeElement.querySelector<HTMLElement>(`#contar-cantidad-${varianteId}`);
  }

  /**
   * Al abrir, el foco entra en la caja. **Faltaba, y sin eso el `(keydown.escape)` de la caja no
   * recibe nunca la tecla**: el foco se queda en el botón que la abrió, que vive en la fila
   * anterior, así que Escape no cancelaba nada. `editar` sí lo hacía; estas tres copiaron la
   * interacción y solo media vuelta del arreglo —el retorno del foco al cancelar—, que es
   * exactamente lo que `shared/foco/foco.ts` documenta que pasó con este patrón.
   */
  protected abrir(varianteId: string): void {
    this.form.reset({ cantidadContada: null, motivo: '' });
    this.error.set(null);
    this.ajuste.set(null);
    this.contando.set(varianteId);
    this.enfocarDespuesDePintar(() => this.primerCampoDe(varianteId));
  }

  /**
   * Cancelar destruye el formulario con el botón "Cancelar" dentro, así que el foco hay que
   * devolverlo a mano al botón que lo abrió: si no, el navegador lo manda a `<body>`.
   */
  protected cancelar(varianteId: string): void {
    this.contando.set(null);
    this.error.set(null);
    this.enfocarDespuesDePintar(() => this.botonDeAbrir(varianteId));
  }

  protected enviar(varianteId: string): void {
    // Guarda de reentrada en vez de `[cargando]` en el botón de enviar: deshabilitarlo mientras
    // va la petición le quita el foco a quien acaba de pulsarlo.
    if (this.enviando()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      // Decir qué falta en vez de solo marcar, y sin deshabilitar el botón: un `<button disabled>`
      // sale del orden de tabulación y quien navega con teclado no llega a enterarse de por qué no
      // pasa nada. Mismo criterio que la pantalla de sin-medir.
      this.error.set(this.transloco.translate('admin.productos.existencias.faltanCampos'));
      return;
    }
    this.error.set(null);

    const valores = this.form.getRawValue();
    this.mutacion.mutate(
      {
        varianteId,
        cantidadContada: valores.cantidadContada ?? 0,
        motivo: valores.motivo.trim(),
      },
      {
        onSuccess: (resultado) => {
          this.contando.set(null);
          this.ajuste.set(resultado);
          // El formulario ya no existe: el foco va al aviso, que es lo que explica qué pasó —y en
          // el peor caso, que el conteo dejó reservas sin respaldo.
          this.enfocarDespuesDePintar(() => this.avisoExistencias()?.nativeElement);
        },
        onError: () =>
          this.error.set(this.transloco.translate('admin.productos.existencias.error')),
      },
    );
  }
}
