import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { mensajeDeError } from '../../../../../core/errores/mensaje-de-error';
import { usarTraductor } from '../../../../../core/i18n/traductor';
import { usarFoco, usarFocoEnPrimerInvalido } from '../../../../../shared/foco/foco';
import { TsEsqueleto } from '../../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsMigas } from '../../../../../shared/ts-migas/ts-migas';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { TsCampo } from '../../../../../shared/ui/campo/ts-campo';
import { usarMigasAdmin } from '../../../migas-admin';
import { usarAccionesReferenciasEnvio } from '../../application/acciones-referencias-envio.mutaciones';
import { usarReferenciasEnvio } from '../../application/referencias-envio.consulta';
import { CategoriaConPeso, LineaConPromedio } from '../../domain/referencias-envio.model';

const CLAVE_LINEA: Record<LineaConPromedio, string> = {
  ROPA: 'admin.referencias_envio.lineas.ROPA',
  CALZADO: 'admin.referencias_envio.lineas.CALZADO',
  BOLSOS: 'admin.referencias_envio.lineas.BOLSOS',
};

/**
 * Los pesos y las medidas con los que se cotiza el envío de lo que no se ha medido (`ADR-0071`).
 *
 * <p>La ropa, el calzado y los bolsos sin medir viajan juntos en una bolsa: las medidas de esa
 * bolsa son una sola para las tres líneas, y el peso es la suma del promedio de cada prenda. Las
 * dos cosas se cambian aquí, sin desplegar nada, y valen desde el siguiente checkout.
 *
 * <p><b>Las categorías sin peso se muestran, y en rojo.</b> Son las que importan: sus productos sin
 * medir solo se venden con recogida en el punto, y una pantalla que solo listara las que ya tienen
 * peso escondería justo las que hay que llenar.
 *
 * <p>El formulario de un peso se abre en su fila, con el mismo patrón que la pantalla de medidas
 * de variantes: el foco entra al abrir, vuelve al botón al cancelar y va al aviso al guardar.
 */
@Component({
  selector: 'app-referencias-envio',
  imports: [ReactiveFormsModule, TranslocoPipe, TsBoton, TsCampo, TsEsqueleto, TsMigas],
  templateUrl: './referencias-envio.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReferenciasEnvioPage {
  protected readonly migas = usarMigasAdmin([{ clave: 'admin.referencias_envio.titulo' }]);

  private readonly transloco = inject(TranslocoService);
  private readonly traducir = usarTraductor();
  private readonly enfocarPrimerInvalido = usarFocoEnPrimerInvalido();
  private readonly enfocarDespuesDePintar = usarFoco();
  private readonly raiz = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly aviso = viewChild<ElementRef<HTMLElement>>('aviso');

  protected readonly consulta = usarReferenciasEnvio();
  private readonly acciones = usarAccionesReferenciasEnvio();

  protected readonly categorias = computed<readonly CategoriaConPeso[]>(
    () => this.consulta.data()?.categorias ?? [],
  );
  protected readonly sinPeso = computed(
    () => this.categorias().filter((categoria) => categoria.pesoGramos === null).length,
  );
  protected readonly sinMedidas = computed(
    () => this.consulta.isSuccess() && this.consulta.data()?.medidas === null,
  );

  /** Lo que se acaba de guardar, para la región viva. Siempre en el DOM; cambia su contenido. */
  protected readonly avisoGuardado = signal<string | null>(null);

  // --- las medidas de la bolsa ------------------------------------------------------------------

  protected readonly formMedidas = new FormGroup({
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
  protected readonly errorMedidas = signal<string | null>(null);
  protected readonly guardandoMedidas = computed(() => this.acciones.fijarMedidas.isPending());

  /**
   * El formulario arranca con las medidas que hay, no en blanco: cambiar el alto es cambiar un
   * número, no volver a escribir los tres. Se rellena cada vez que llegan unas medidas distintas
   * —al cargar y después de guardar—, comparando las cifras y no la referencia del objeto, que una
   * revalidación cambia sin que nada haya cambiado.
   */
  private readonly clavePrevia = signal<string | null>(null);
  private readonly rellenarMedidas = effect(() => {
    const medidas = this.consulta.data()?.medidas ?? null;
    const clave = medidas ? `${medidas.largoCm}x${medidas.anchoCm}x${medidas.altoCm}` : 'sin';
    if (untracked(this.clavePrevia) === clave) {
      return;
    }
    this.clavePrevia.set(clave);
    if (medidas) {
      this.formMedidas.reset({ ...medidas });
    }
  });

  /**
   * Guarda de reentrada con `[ocupado]` y no `[cargando]`: la página tiene más acciones debajo, y
   * deshabilitar el botón recién pulsado le quitaría el foco a quien lo pulsó si el guardado falla.
   */
  protected guardarMedidas(): void {
    if (this.guardandoMedidas()) {
      return;
    }
    if (this.formMedidas.invalid) {
      this.formMedidas.markAllAsTouched();
      this.enfocarPrimerInvalido();
      this.errorMedidas.set(this.transloco.translate('admin.referencias_envio.faltan_medidas'));
      return;
    }
    this.errorMedidas.set(null);
    const valores = this.formMedidas.getRawValue();
    this.acciones.fijarMedidas.mutate(
      {
        largoCm: valores.largoCm ?? 0,
        anchoCm: valores.anchoCm ?? 0,
        altoCm: valores.altoCm ?? 0,
      },
      {
        onSuccess: () => {
          this.avisoGuardado.set(
            this.transloco.translate('admin.referencias_envio.medidas_guardadas'),
          );
          this.enfocarDespuesDePintar(() => this.aviso()?.nativeElement);
        },
        onError: (error) =>
          this.errorMedidas.set(
            mensajeDeError(error, this.transloco, 'admin.referencias_envio.error_medidas'),
          ),
      },
    );
  }

  // --- el peso de cada categoría ----------------------------------------------------------------

  /** La fila con el formulario abierto. `null` = ninguna. */
  protected readonly editando = signal<string | null>(null);
  protected readonly errorPeso = signal<string | null>(null);
  protected readonly ocupado = computed(
    () => this.acciones.fijarPeso.isPending() || this.acciones.quitarPeso.isPending(),
  );

  protected readonly formPeso = new FormGroup({
    pesoGramos: new FormControl<number | null>(null, {
      validators: [Validators.required, Validators.min(1)],
    }),
  });

  /**
   * El error de un campo, pegado al campo: con él `ts-campo` pinta el borde, pone `aria-invalid` y
   * lo ata con `aria-describedby`. El mensaje del formulario dice qué falta en general; sin este, el
   * `markAllAsTouched()` no marcaba ningún campo.
   */
  protected errorDe(control: AbstractControl, clave: string): string | null {
    return control.touched && control.invalid ? this.traducir()(clave) : null;
  }

  protected etiquetaLinea(linea: LineaConPromedio): string {
    return this.traducir()(CLAVE_LINEA[linea]);
  }

  /** "Dama › Jeans", o solo "Unisex" si cuelga directo de la línea. */
  protected nombreCompleto(categoria: CategoriaConPeso): string {
    return categoria.rama ? `${categoria.rama} › ${categoria.nombre}` : categoria.nombre;
  }

  protected abrir(categoria: CategoriaConPeso): void {
    this.formPeso.reset({ pesoGramos: categoria.pesoGramos });
    this.errorPeso.set(null);
    this.avisoGuardado.set(null);
    this.editando.set(categoria.categoriaId);
    this.enfocarDespuesDePintar(() =>
      this.raiz.nativeElement.querySelector<HTMLElement>(`#peso-${categoria.categoriaId}`),
    );
  }

  /** Cancelar destruye el formulario con el botón dentro: el foco vuelve al que lo abrió. */
  protected cancelar(categoriaId: string): void {
    this.editando.set(null);
    this.errorPeso.set(null);
    this.enfocarDespuesDePintar(() => this.botonDeAbrir(categoriaId));
  }

  protected guardarPeso(categoria: CategoriaConPeso): void {
    // Guarda de reentrada en vez de `[cargando]`: deshabilitar el botón recién pulsado le quita
    // el foco a quien lo pulsó.
    if (this.ocupado()) {
      return;
    }
    if (this.formPeso.invalid) {
      this.formPeso.markAllAsTouched();
      this.enfocarPrimerInvalido();
      this.errorPeso.set(this.transloco.translate('admin.referencias_envio.falta_peso'));
      return;
    }
    this.errorPeso.set(null);
    const pesoGramos = this.formPeso.getRawValue().pesoGramos ?? 0;
    this.acciones.fijarPeso.mutate(
      { categoriaId: categoria.categoriaId, pesoGramos },
      {
        onSuccess: () =>
          this.cerrarConAviso('admin.referencias_envio.peso_guardado', categoria, pesoGramos),
        onError: (error) =>
          this.errorPeso.set(
            mensajeDeError(error, this.transloco, 'admin.referencias_envio.error_peso'),
          ),
      },
    );
  }

  protected quitarPeso(categoria: CategoriaConPeso): void {
    if (this.ocupado()) {
      return;
    }
    this.errorPeso.set(null);
    this.acciones.quitarPeso.mutate(categoria.categoriaId, {
      onSuccess: () => this.cerrarConAviso('admin.referencias_envio.peso_quitado', categoria),
      onError: (error) =>
        this.errorPeso.set(
          mensajeDeError(error, this.transloco, 'admin.referencias_envio.error_peso'),
        ),
    });
  }

  private cerrarConAviso(clave: string, categoria: CategoriaConPeso, peso?: number): void {
    this.editando.set(null);
    this.avisoGuardado.set(
      this.transloco.translate(clave, { categoria: this.nombreCompleto(categoria), peso }),
    );
    this.enfocarDespuesDePintar(() => this.aviso()?.nativeElement);
  }

  /** El `<button>` real dentro del `ts-boton` que abre el formulario de una fila. */
  private botonDeAbrir(categoriaId: string): HTMLElement | null {
    return this.raiz.nativeElement.querySelector<HTMLElement>(
      `[data-abrir="${categoriaId}"] button`,
    );
  }
}
