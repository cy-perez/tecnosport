import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { mensajeDeError } from '../../../../../core/errores/mensaje-de-error';
import { fechaConHora } from '../../../../../core/i18n/fecha-colombia';
import { usarTraductor } from '../../../../../core/i18n/traductor';
import { usarFoco } from '../../../../../shared/foco/foco';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { TsEsqueleto } from '../../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsMigas } from '../../../../../shared/ts-migas/ts-migas';
import { TsPaginador } from '../../../../../shared/ts-paginador/ts-paginador';
import { usarMigasAdmin } from '../../../migas-admin';
import { usarProveedoresAdmin } from '../../../proveedores/application/listar-proveedores.consulta';
import {
  usarArchivosDeIngesta,
  usarBorrarArchivoDeIngesta,
} from '../../application/archivos-de-ingesta.consulta';
import {
  ArchivoDeIngesta,
  formatearTamano,
  sePuedeBorrarArchivo,
} from '../../domain/archivo-de-ingesta.model';

/**
 * El historial de los zips subidos para una ingesta, con un botón por archivo para la limpieza
 * periódica del almacenamiento. Borrar se lleva solo el zip: los lotes, sus borradores y sus fotos
 * se quedan, y la lista de ingestas sigue igual. Con un lote que lo lee todavía abierto no se
 * ofrece; el servidor respondería 409.
 *
 * La pregunta va en su propia fila, como en la lista de ingestas. La fila se queda al borrar —el
 * archivo pasa a "borrado"— pero el botón se va, así que el foco va al aviso.
 */
@Component({
  selector: 'app-archivos-de-ingesta-admin',
  imports: [TranslocoPipe, TsBoton, TsEsqueleto, TsMigas, TsPaginador],
  templateUrl: './archivos-de-ingesta-admin.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ArchivosDeIngestaAdminPage {
  protected readonly migas = usarMigasAdmin([
    { clave: 'admin.ingestas.titulo', ruta: ['ingestas'] },
    { clave: 'admin.ingestas.archivos.titulo' },
  ]);

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);
  private readonly traducir = usarTraductor();

  private readonly queryParams = toSignal(this.route.queryParams, {
    initialValue: this.route.snapshot.queryParams,
  });
  /** En la URL, como en las demás listas del panel: sobrevive a recargar y a "atrás". */
  protected readonly pagina = computed(() => {
    const valor = Number(this.queryParams()['pagina']);
    return Number.isInteger(valor) && valor > 0 ? valor : 0;
  });

  protected readonly consulta = usarArchivosDeIngesta(this.pagina);
  protected readonly proveedores = usarProveedoresAdmin();
  protected readonly archivos = computed<readonly ArchivoDeIngesta[]>(
    () => this.consulta.data()?.items ?? [],
  );
  protected readonly total = computed(() => this.consulta.data()?.totalArchivos ?? 0);
  private readonly nombresDeProveedor = computed(
    () => new Map((this.proveedores.data() ?? []).map((p) => [p.id, p.nombre] as const)),
  );

  private readonly borrado = usarBorrarArchivoDeIngesta();
  private borradoEnVuelo = false;
  protected readonly borrando = computed(() => this.borrado.isPending());
  /** El archivo cuya fila está preguntando: una sola a la vez. */
  protected readonly confirmando = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);

  private readonly enfocarDespuesDePintar = usarFoco();
  private readonly caja = viewChild<ElementRef<HTMLElement>>('caja');
  private readonly avisoRef = viewChild<ElementRef<HTMLElement>>('avisoRef');
  private readonly raiz = inject<ElementRef<HTMLElement>>(ElementRef);

  protected sePuedeBorrar(archivo: ArchivoDeIngesta): boolean {
    return sePuedeBorrarArchivo(archivo);
  }

  protected nombre(archivo: ArchivoDeIngesta): string {
    return archivo.nombreOriginal ?? this.traducir()('admin.ingestas.archivos.sinNombre');
  }

  protected nombreDelProveedor(archivo: ArchivoDeIngesta): string {
    return this.nombresDeProveedor().get(archivo.proveedorId) ?? '';
  }

  protected tamano(archivo: ArchivoDeIngesta): string | null {
    return archivo.tamanoBytes === null
      ? null
      : formatearTamano(archivo.tamanoBytes, this.transloco.activeLang());
  }

  protected formatearFecha(iso: string): string {
    return iso ? fechaConHora(iso, this.transloco.activeLang()) : '';
  }

  protected irAPagina(pagina: number): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { pagina: pagina > 0 ? pagina : null },
    });
  }

  protected preguntar(archivo: ArchivoDeIngesta): void {
    this.error.set(null);
    this.aviso.set(null);
    this.confirmando.set(archivo.id);
    this.enfocarDespuesDePintar(() => this.caja()?.nativeElement);
  }

  protected cancelar(): void {
    const id = this.confirmando();
    this.confirmando.set(null);
    this.error.set(null);
    this.enfocarDespuesDePintar(() =>
      this.raiz.nativeElement.querySelector<HTMLElement>(`[data-borrar="${id}"] button`),
    );
  }

  /** Guarda de reentrada: el botón usa `[ocupado]`, no `[cargando]`, y sigue siendo pulsable. */
  protected borrar(archivo: ArchivoDeIngesta): void {
    if (this.borradoEnVuelo) {
      return;
    }
    this.borradoEnVuelo = true;
    this.error.set(null);
    const nombre = this.nombre(archivo);
    this.borrado.mutate(archivo.id, {
      onSettled: () => (this.borradoEnVuelo = false),
      onSuccess: () => {
        this.confirmando.set(null);
        this.aviso.set(
          this.transloco.translate('admin.ingestas.archivos.borrar.hecho', { nombre }),
        );
        this.enfocarDespuesDePintar(() => this.avisoRef()?.nativeElement);
      },
      onError: (error: unknown) =>
        this.error.set(
          mensajeDeError(error, this.transloco, 'admin.ingestas.archivos.borrar.error'),
        ),
    });
  }
}
