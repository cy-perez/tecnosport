import { isPlatformBrowser } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  input,
  PLATFORM_ID,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { mensajeDeError } from '../../../../../core/errores/mensaje-de-error';
import { usarIdiomaActivo } from '../../../../../core/i18n/traductor';
import { usarFoco } from '../../../../../shared/foco/foco';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { TsCampo } from '../../../../../shared/ui/campo/ts-campo';
import {
  usarQuitarFotoBorradorTecnologia,
  usarReordenarFotosBorradorTecnologia,
  usarSubirFotoBorradorTecnologia,
} from '../../application/editar-borrador-tecnologia.mutacion';
import {
  BorradorTecnologia,
  FotoDeBorradorTecnologia,
} from '../../domain/borrador-tecnologia.model';

/** La principal y las nueve de la galería: `BorradorTecnologia.TOPE_DE_FOTOS` en la API. */
export const TOPE_DE_FOTOS_DEL_BORRADOR = 10;

/** Los que acepta el bucket del borrador y el navegador sabe medir. */
const TIPOS_ACEPTADOS = ['image/jpeg', 'image/png', 'image/webp', 'image/avif'];

/**
 * Las fotos del borrador de tecnología: verlas siempre, y mientras está en revisión subir, quitar,
 * reordenar y elegir la principal (23/10/2026). Las de la skill pasaron la revisión del bloque 2;
 * las que se suben aquí no, y lo decide quien revisa.
 *
 * Mismas interacciones que la galería de un producto: «Subir» y «Bajar» se quitan en cada extremo
 * en vez de deshabilitarse, quitar pide confirmación en línea, y el foco vuelve a un sitio que
 * existe cuando el botón que se pulsó desaparece.
 */
@Component({
  selector: 'ts-fotos-borrador-tecnologia',
  imports: [ReactiveFormsModule, TranslocoPipe, TsBoton, TsCampo],
  templateUrl: './ts-fotos-borrador-tecnologia.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsFotosBorradorTecnologia {
  private readonly transloco = inject(TranslocoService);
  private readonly esNavegador = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly anfitrion = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly enfocar = usarFoco();
  private readonly subir = usarSubirFotoBorradorTecnologia();
  private readonly quitar = usarQuitarFotoBorradorTecnologia();
  private readonly reordenar = usarReordenarFotosBorradorTecnologia();
  protected readonly idioma = usarIdiomaActivo();

  readonly borrador = input.required<BorradorTecnologia>();
  readonly editable = input(false);

  protected readonly tope = TOPE_DE_FOTOS_DEL_BORRADOR;
  protected readonly fotos = computed(() => this.borrador().fotos);
  protected readonly llena = computed(() => this.fotos().length >= TOPE_DE_FOTOS_DEL_BORRADOR);

  protected readonly confirmandoQuitar = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly errorSubida = signal<string | null>(null);
  protected readonly quitando = computed(() => this.quitar.isPending());
  protected readonly reordenando = computed(() => this.reordenar.isPending());
  protected readonly subiendo = computed(() => this.subir.isPending());

  protected readonly formulario = new FormGroup({
    altEs: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    altEn: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });
  protected readonly archivo = signal<File | null>(null);
  protected readonly previsualizacion = signal<string | null>(null);
  private dimensiones: { ancho: number; alto: number } | null = null;

  private readonly avisoRef = viewChild<ElementRef<HTMLElement>>('avisoRef');
  private readonly campoArchivo = viewChild<ElementRef<HTMLInputElement>>('campoArchivo');

  constructor() {
    // El texto alternativo nace con el nombre del modelo, como el de las fotos de la skill: se
    // escribe una vez por borrador, no cada vez que la consulta se revalida.
    let para: string | null = null;
    effect(() => {
      const borrador = this.borrador();
      if (para === borrador.id) {
        return;
      }
      para = borrador.id;
      untracked(() => this.prellenarAlt(borrador.titulo));
    });
  }

  private prellenarAlt(titulo: string): void {
    this.formulario.setValue({
      altEs: this.transloco.translate('admin.tecnologia.fotos.altSugeridoEs', { modelo: titulo }),
      altEn: this.transloco.translate('admin.tecnologia.fotos.altSugeridoEn', { modelo: titulo }),
    });
  }

  protected altDe(foto: FotoDeBorradorTecnologia): string {
    return this.idioma() === 'en' ? foto.altEn : foto.altEs;
  }

  /** Sube o baja una posición; la primera es la principal. */
  protected mover(foto: FotoDeBorradorTecnologia, paso: -1 | 1): void {
    const ids = this.fotos().map((f) => f.id);
    const desde = ids.indexOf(foto.id);
    const hasta = desde + paso;
    if (desde < 0 || hasta < 0 || hasta >= ids.length) {
      return;
    }
    [ids[desde], ids[hasta]] = [ids[hasta], ids[desde]];
    this.aplicarOrden(ids, foto.id, paso === -1 ? 'subir' : 'bajar');
  }

  protected usarComoPrincipal(foto: FotoDeBorradorTecnologia): void {
    const ids = [
      foto.id,
      ...this.fotos()
        .map((f) => f.id)
        .filter((id) => id !== foto.id),
    ];
    this.aplicarOrden(ids, null, null);
  }

  private aplicarOrden(ids: string[], fotoId: string | null, accion: 'subir' | 'bajar' | null) {
    if (this.reordenando()) {
      return;
    }
    this.error.set(null);
    this.aviso.set(null);
    this.reordenar.mutate(
      { id: this.borrador().id, fotoIds: ids },
      {
        onSuccess: () => {
          this.aviso.set(
            accion === null
              ? 'admin.tecnologia.fotos.principalCambiada'
              : 'admin.tecnologia.fotos.ordenGuardado',
          );
          // La foto cambió de sitio con sus botones: el foco la sigue. En un extremo el botón que
          // se pulsó ya no existe y se pasa al otro; sin ninguno, al aviso.
          this.enfocar(() => {
            const fila =
              fotoId === null
                ? null
                : this.anfitrion.nativeElement.querySelector(`[data-foto-id="${fotoId}"]`);
            const otro = accion === 'subir' ? 'bajar' : 'subir';
            return (
              fila?.querySelector<HTMLElement>(`ts-boton[data-mover="${accion}"] button`) ??
              fila?.querySelector<HTMLElement>(`ts-boton[data-mover="${otro}"] button`) ??
              this.avisoRef()?.nativeElement
            );
          });
        },
        onError: (error) =>
          this.error.set(
            mensajeDeError(error, this.transloco, 'admin.tecnologia.fotos.errorOrden'),
          ),
      },
    );
  }

  protected preguntarSiQuitar(foto: FotoDeBorradorTecnologia): void {
    this.error.set(null);
    this.confirmandoQuitar.set(foto.id);
    this.enfocar(() =>
      this.anfitrion.nativeElement.querySelector<HTMLElement>(`#quitar-foto-caja-${foto.id}`),
    );
  }

  protected cancelarQuitar(): void {
    const id = this.confirmandoQuitar();
    this.confirmandoQuitar.set(null);
    this.enfocar(() =>
      this.anfitrion.nativeElement.querySelector<HTMLElement>(
        `[data-foto-id="${id}"] ts-boton[data-quitar] button`,
      ),
    );
  }

  protected confirmarQuitar(foto: FotoDeBorradorTecnologia): void {
    if (this.quitando()) {
      return;
    }
    this.quitar.mutate(
      { id: this.borrador().id, fotoId: foto.id },
      {
        onSuccess: () => {
          this.confirmandoQuitar.set(null);
          this.aviso.set('admin.tecnologia.fotos.quitada');
          this.enfocar(() => this.avisoRef()?.nativeElement);
        },
        onError: (error) =>
          this.error.set(
            mensajeDeError(error, this.transloco, 'admin.tecnologia.fotos.errorQuitar'),
          ),
      },
    );
  }

  protected async elegirArchivo(evento: Event): Promise<void> {
    if (!this.esNavegador) {
      return;
    }
    const campo = evento.target as HTMLInputElement;
    const elegido = campo.files?.[0] ?? null;
    this.errorSubida.set(null);
    this.limpiarPrevisualizacion();
    this.archivo.set(null);
    this.dimensiones = null;
    if (!elegido) {
      return;
    }
    if (!TIPOS_ACEPTADOS.includes(elegido.type)) {
      this.errorSubida.set(this.transloco.translate('admin.tecnologia.fotos.tipoNoSoportado'));
      campo.value = '';
      return;
    }
    const url = URL.createObjectURL(elegido);
    try {
      this.dimensiones = await leerDimensiones(url);
      this.previsualizacion.set(url);
      this.archivo.set(elegido);
    } catch {
      URL.revokeObjectURL(url);
      this.errorSubida.set(this.transloco.translate('admin.tecnologia.fotos.ilegible'));
    }
  }

  protected agregar(): void {
    if (this.subiendo()) {
      return;
    }
    const archivo = this.archivo();
    if (!archivo || !this.dimensiones || this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      this.errorSubida.set(this.transloco.translate('admin.tecnologia.fotos.faltanCampos'));
      return;
    }
    this.errorSubida.set(null);
    this.aviso.set(null);
    const { altEs, altEn } = this.formulario.getRawValue();
    this.subir.mutate(
      {
        id: this.borrador().id,
        foto: { archivo, ...this.dimensiones, altEs: altEs.trim(), altEn: altEn.trim() },
      },
      {
        onSuccess: () => {
          this.limpiarPrevisualizacion();
          this.archivo.set(null);
          this.dimensiones = null;
          const campo = this.campoArchivo()?.nativeElement;
          if (campo) {
            campo.value = '';
          }
          this.prellenarAlt(this.borrador().titulo);
          this.aviso.set('admin.tecnologia.fotos.agregada');
          this.enfocar(() => this.avisoRef()?.nativeElement);
        },
        onError: (error) =>
          this.errorSubida.set(
            mensajeDeError(error, this.transloco, 'admin.tecnologia.fotos.errorSubir'),
          ),
      },
    );
  }

  private limpiarPrevisualizacion(): void {
    const anterior = this.previsualizacion();
    if (anterior) {
      URL.revokeObjectURL(anterior);
    }
    this.previsualizacion.set(null);
  }
}

function leerDimensiones(url: string): Promise<{ ancho: number; alto: number }> {
  return new Promise((resolve, reject) => {
    const imagen = new Image();
    imagen.onload = () => resolve({ ancho: imagen.naturalWidth, alto: imagen.naturalHeight });
    imagen.onerror = () => reject(new Error('No se pudo leer la imagen.'));
    imagen.src = url;
  });
}
