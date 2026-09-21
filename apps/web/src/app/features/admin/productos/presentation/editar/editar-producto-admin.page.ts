import { isPlatformBrowser } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  PLATFORM_ID,
  signal,
  viewChild,
  viewChildren,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { usarOpcionesFiltro } from '../../../../catalogo/application/listar-opciones-filtro.consulta';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { TsPaginaFormulario } from '../../../../../shared/ui/pagina-formulario/ts-pagina-formulario';
import { TsCampo } from '../../../../../shared/ui/campo/ts-campo';
import { TsEsqueleto } from '../../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsMigas } from '../../../../../shared/ts-migas/ts-migas';
import { OpcionSelect, TsSelect } from '../../../../../shared/ui/select/ts-select';
import { TsSelectControl } from '../../../../../shared/ui/select/ts-select-control';
import { usarMigasAdmin } from '../../../migas-admin';
import { usarEditarProductoAdmin } from '../../application/editar-producto-admin.mutacion';
import { usarSubirImagenPrincipalAdmin } from '../../application/subir-imagen-principal-admin.mutacion';
import { usarSubirImagenDeGaleriaAdmin } from '../../application/subir-imagen-de-galeria-admin.mutacion';
import { usarQuitarImagenDeGaleriaAdmin } from '../../application/quitar-imagen-de-galeria-admin.mutacion';
import { usarReordenarGaleriaAdmin } from '../../application/reordenar-galeria-admin.mutacion';
import { mensajeDeError } from '../../../../../core/errores/mensaje-de-error';
import { ImagenDeGaleriaAdmin } from '../../domain/producto-admin.model';
import { usarVerProductoAdmin } from '../../application/ver-producto-admin.consulta';

const TIPOS_DE_IMAGEN_SOPORTADOS = ['image/jpeg', 'image/png', 'image/webp'];

/**
 * El mismo tope que `Producto.TOPE_DE_GALERIA` en el backend, que es quien manda: esto solo sirve
 * para decirlo en la ayuda y para no ofrecer un formulario que va a responder 409. Si un día
 * divergen, el que decide sigue siendo el servidor.
 */
const TOPE_DE_GALERIA = 8;

@Component({
  selector: 'app-editar-producto-admin',
  imports: [
    TsPaginaFormulario,
    ReactiveFormsModule,
    RouterLink,
    TranslocoPipe,
    TsBoton,
    TsCampo,
    TsEsqueleto,
    TsMigas,
    TsSelect,
    TsSelectControl,
  ],
  templateUrl: './editar-producto-admin.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditarProductoAdminPage {
  protected readonly migas = usarMigasAdmin([
    { clave: 'admin.productos.titulo', ruta: ['productos'] },
    { clave: 'admin.productos.editar.titulo' },
  ]);

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);
  private readonly opciones = usarOpcionesFiltro();
  private readonly mutacion = usarEditarProductoAdmin();
  private readonly mutacionImagen = usarSubirImagenPrincipalAdmin();
  private readonly mutacionGaleria = usarSubirImagenDeGaleriaAdmin();
  private readonly mutacionQuitarDeGaleria = usarQuitarImagenDeGaleriaAdmin();
  private readonly mutacionReordenarGaleria = usarReordenarGaleriaAdmin();
  private readonly esNavegador = isPlatformBrowser(inject(PLATFORM_ID));

  private readonly paramMap = toSignal(this.route.paramMap, {
    initialValue: this.route.snapshot.paramMap,
  });
  protected readonly id = computed(() => this.paramMap().get('id') ?? '');

  /**
   * Absoluto, no relativo. La ruta de esta pantalla es `:id/editar`, dos
   * segmentos, así que un `..` sube uno solo y deja `productos/:id`: el enlace
   * relativo generaba `productos/{id}/{id}/variantes/crear`, que no existe, y
   * al hacer clic la aplicación caía en la portada. Mismo criterio que
   * `usarMigasAdmin` y `ts-tarjeta-producto`, y misma familia de error que ya
   * apareció en el paso 2 de Track B con un path sin prefijo de idioma.
   */
  protected readonly enlaceAgregarVariante = computed(() => [
    '/',
    this.transloco.activeLang(),
    'admin',
    'productos',
    this.id(),
    'variantes',
    'crear',
  ]);

  protected readonly consulta = usarVerProductoAdmin(this.id);

  protected readonly error = signal<string | null>(null);
  private prefilled = false;

  protected readonly form = new FormGroup({
    nombre: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    descripcion: new FormControl('', { nonNullable: true }),
    marcaId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    categoriaId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  private readonly valorFormulario = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });
  protected readonly formularioInvalido = computed(() => {
    this.valorFormulario();
    return this.form.invalid;
  });

  protected readonly enviando = computed(() => this.mutacion.isPending());

  protected readonly opcionesMarca = computed<OpcionSelect[]>(() =>
    (this.opciones.marcas.data() ?? []).map((marca) => ({
      valor: marca.id,
      etiqueta: marca.nombre,
    })),
  );

  protected readonly opcionesCategoria = computed<OpcionSelect[]>(() =>
    (this.opciones.categorias.data() ?? []).map((categoria) => ({
      valor: categoria.id,
      etiqueta: categoria.nombre,
    })),
  );

  protected readonly formularioImagen = new FormGroup({
    altEs: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    altEn: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  private readonly valorFormularioImagen = toSignal(this.formularioImagen.valueChanges, {
    initialValue: this.formularioImagen.getRawValue(),
  });

  protected readonly archivoSeleccionado = signal<File | null>(null);
  protected readonly previsualizacionUrl = signal<string | null>(null);
  private dimensionesArchivo: { ancho: number; alto: number } | null = null;
  protected readonly errorImagen = signal<string | null>(null);

  protected readonly imagenListaParaSubir = computed(() => {
    this.valorFormularioImagen();
    return this.archivoSeleccionado() !== null && !this.formularioImagen.invalid;
  });

  protected readonly subiendoImagen = computed(() => this.mutacionImagen.isPending());

  protected readonly topeDeGaleria = TOPE_DE_GALERIA;

  protected readonly formularioGaleria = new FormGroup({
    altEs: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    altEn: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  private readonly valorFormularioGaleria = toSignal(this.formularioGaleria.valueChanges, {
    initialValue: this.formularioGaleria.getRawValue(),
  });

  protected readonly archivoDeGaleria = signal<File | null>(null);
  protected readonly previsualizacionGaleria = signal<string | null>(null);
  private dimensionesGaleria: { ancho: number; alto: number } | null = null;
  protected readonly errorGaleria = signal<string | null>(null);

  /**
   * El fallo de quitar tiene su propia señal, y no es cosmética: `errorGaleria` solo se pinta
   * dentro del formulario de agregar, así que "no se pudo quitar la imagen" salía a varios cientos
   * de píxeles de la fila que falló y colocado como si fuera un error de la subida.
   */
  protected readonly errorQuitar = signal<string | null>(null);

  /** Una sola región viva para los dos anuncios; guarda la clave, no el texto. */
  protected readonly aviso = signal<string | null>(null);

  /** El id de la imagen cuya fila está preguntando, como en la lista de productos. */
  protected readonly confirmandoQuitar = signal<string | null>(null);

  /**
   * El foco no puede quedarse donde estaba: al confirmar desaparece la fila entera con su botón
   * dentro, y al cancelar desaparece la caja. En los dos casos el navegador lo manda a `<body>` y
   * quien navega con teclado vuelve al principio del documento.
   */
  private readonly cajaConfirmacion = viewChild<ElementRef<HTMLElement>>('cajaConfirmacion');
  private readonly avisoGaleria = viewChild<ElementRef<HTMLElement>>('avisoGaleria');
  private readonly filas = viewChildren<ElementRef<HTMLElement>>('filaDeGaleria');

  protected readonly galeria = computed<readonly ImagenDeGaleriaAdmin[]>(
    () => this.consulta.data()?.galeria ?? [],
  );

  protected readonly galeriaLlena = computed(() => this.galeria().length >= TOPE_DE_GALERIA);

  protected readonly imagenDeGaleriaListaParaSubir = computed(() => {
    this.valorFormularioGaleria();
    return (
      this.archivoDeGaleria() !== null && !this.formularioGaleria.invalid && !this.galeriaLlena()
    );
  });

  protected readonly agregandoAGaleria = computed(() => this.mutacionGaleria.isPending());
  protected readonly quitandoDeGaleria = computed(() => this.mutacionQuitarDeGaleria.isPending());
  protected readonly reordenandoGaleria = computed(() => this.mutacionReordenarGaleria.isPending());

  /**
   * El fallo de reordenar va aparte del de quitar por el mismo motivo por el que aquel se separó
   * del de agregar: se pinta donde pasó.
   */
  protected readonly errorReordenar = signal<string | null>(null);

  constructor() {
    effect(() => {
      const producto = this.consulta.data();
      if (producto && !this.prefilled) {
        this.prefilled = true;
        this.form.patchValue({
          nombre: producto.nombre,
          descripcion: producto.descripcion,
          marcaId: producto.marca.id,
          categoriaId: producto.categoria.id,
        });
      }
    });
  }

  protected enviar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.error.set(null);

    const valores = this.form.getRawValue();
    this.mutacion.mutate(
      {
        id: this.id(),
        comando: {
          nombre: valores.nombre,
          descripcion: valores.descripcion,
          marcaId: valores.marcaId,
          categoriaId: valores.categoriaId,
        },
      },
      {
        onSuccess: () =>
          void this.router.navigate(['/' + this.transloco.activeLang(), 'admin', 'productos']),
        onError: () => this.error.set(this.transloco.translate('admin.productos.editar.error')),
      },
    );
  }

  protected async onArchivoSeleccionado(evento: Event): Promise<void> {
    if (!this.esNavegador) {
      return;
    }
    const input = evento.target as HTMLInputElement;
    const archivo = input.files?.[0] ?? null;
    this.errorImagen.set(null);
    this.limpiarPrevisualizacion();
    if (!archivo) {
      this.archivoSeleccionado.set(null);
      return;
    }
    if (!TIPOS_DE_IMAGEN_SOPORTADOS.includes(archivo.type)) {
      this.errorImagen.set(
        this.transloco.translate('admin.productos.editar.imagenPrincipal.tipoNoSoportado'),
      );
      input.value = '';
      return;
    }

    const url = URL.createObjectURL(archivo);
    try {
      this.dimensionesArchivo = await this.leerDimensiones(url);
      this.previsualizacionUrl.set(url);
      this.archivoSeleccionado.set(archivo);
    } catch {
      URL.revokeObjectURL(url);
      this.errorImagen.set(
        this.transloco.translate('admin.productos.editar.imagenPrincipal.error'),
      );
    }
  }

  private leerDimensiones(url: string): Promise<{ ancho: number; alto: number }> {
    return new Promise((resolve, reject) => {
      const imagen = new Image();
      imagen.onload = () => resolve({ ancho: imagen.naturalWidth, alto: imagen.naturalHeight });
      imagen.onerror = () => reject(new Error('No se pudo leer la imagen.'));
      imagen.src = url;
    });
  }

  private limpiarPrevisualizacion(): void {
    const anterior = this.previsualizacionUrl();
    if (anterior) {
      URL.revokeObjectURL(anterior);
    }
    this.previsualizacionUrl.set(null);
  }

  protected async onArchivoDeGaleriaSeleccionado(evento: Event): Promise<void> {
    if (!this.esNavegador) {
      return;
    }
    const input = evento.target as HTMLInputElement;
    const archivo = input.files?.[0] ?? null;
    this.errorGaleria.set(null);
    this.aviso.set(null);
    this.limpiarPrevisualizacionGaleria();
    if (!archivo) {
      this.archivoDeGaleria.set(null);
      return;
    }
    if (!TIPOS_DE_IMAGEN_SOPORTADOS.includes(archivo.type)) {
      this.errorGaleria.set(
        this.transloco.translate('admin.productos.editar.galeria.tipoNoSoportado'),
      );
      input.value = '';
      return;
    }

    const url = URL.createObjectURL(archivo);
    try {
      this.dimensionesGaleria = await this.leerDimensiones(url);
      this.previsualizacionGaleria.set(url);
      this.archivoDeGaleria.set(archivo);
    } catch {
      URL.revokeObjectURL(url);
      this.errorGaleria.set(this.transloco.translate('admin.productos.editar.galeria.error'));
    }
  }

  private limpiarPrevisualizacionGaleria(): void {
    const anterior = this.previsualizacionGaleria();
    if (anterior) {
      URL.revokeObjectURL(anterior);
    }
    this.previsualizacionGaleria.set(null);
  }

  protected agregarAGaleria(): void {
    const archivo = this.archivoDeGaleria();
    if (!archivo || !this.dimensionesGaleria || this.formularioGaleria.invalid) {
      this.formularioGaleria.markAllAsTouched();
      return;
    }
    this.errorGaleria.set(null);

    const { altEs, altEn } = this.formularioGaleria.getRawValue();
    this.mutacionGaleria.mutate(
      {
        productoId: this.id(),
        archivo,
        ancho: this.dimensionesGaleria.ancho,
        alto: this.dimensionesGaleria.alto,
        altEs,
        altEn,
      },
      {
        onSuccess: () => {
          this.limpiarPrevisualizacionGaleria();
          this.archivoDeGaleria.set(null);
          this.dimensionesGaleria = null;
          this.formularioGaleria.reset();
          // Un formulario que se vacía se lee igual como "funcionó" que como "nunca se envió".
          this.aviso.set('admin.productos.editar.galeria.agregada');
        },
        // El 409 de la foto repetida y el de la galería llena son accionables, y `mensajeDeError`
        // los dice con sus palabras: "no se pudo" mandaría a mirar el sitio equivocado.
        onError: (error) =>
          this.errorGaleria.set(
            mensajeDeError(error, this.transloco, 'admin.productos.editar.galeria.error'),
          ),
      },
    );
  }

  protected preguntarSiQuitar(imagen: ImagenDeGaleriaAdmin): void {
    // Solo lo suyo: abrir una pregunta no es razón para borrar el error de una subida que falló.
    this.errorQuitar.set(null);
    this.aviso.set(null);
    this.confirmandoQuitar.set(imagen.id);
    // Sin esto la advertencia de que el archivo se borra queda detrás del foco: tabular desde
    // "Quitar" salta directo a "Sí, quitar" y se puede confirmar sin haberla encontrado nunca.
    this.enfocarDespuesDePintar(() => this.cajaConfirmacion()?.nativeElement);
  }

  protected cancelarQuitar(): void {
    const id = this.confirmandoQuitar();
    this.confirmandoQuitar.set(null);
    this.errorQuitar.set(null);
    this.enfocarDespuesDePintar(() => this.botonQuitarDe(id));
  }

  /**
   * El elemento se enfoca en el siguiente cuadro: en el momento de la llamada todavía no existe
   * —lo acaba de crear un `@if`— o está a punto de dejar de existir.
   */
  private enfocarDespuesDePintar(elemento: () => HTMLElement | null | undefined): void {
    if (!this.esNavegador) {
      return;
    }
    requestAnimationFrame(() => elemento()?.focus());
  }

  private botonQuitarDe(imagenId: string | null): HTMLElement | null {
    if (!imagenId) {
      return null;
    }
    const fila = this.filas().find((f) => f.nativeElement.dataset['imagenId'] === imagenId);
    // Por su marca y no por `querySelector('button')`: la fila tiene ahora tres botones —subir,
    // bajar y quitar— y el primero dejó de ser este.
    return fila?.nativeElement.querySelector<HTMLElement>('[data-quitar] button') ?? null;
  }

  /** Borra el archivo además de la fila, así que pregunta antes: no hay vuelta. */
  protected quitarDeGaleria(imagen: ImagenDeGaleriaAdmin): void {
    this.errorQuitar.set(null);
    this.mutacionQuitarDeGaleria.mutate(
      { productoId: this.id(), imagenId: imagen.id },
      {
        onSuccess: () => {
          this.confirmandoQuitar.set(null);
          this.aviso.set('admin.productos.editar.galeria.quitada');
          // La fila y su botón ya no existen; el aviso sí, y dice lo que pasó.
          this.enfocarDespuesDePintar(() => this.avisoGaleria()?.nativeElement);
        },
        onError: (error) =>
          this.errorQuitar.set(
            mensajeDeError(error, this.transloco, 'admin.productos.editar.galeria.errorQuitar'),
          ),
      },
    );
  }

  /**
   * Mueve una imagen un puesto arriba o abajo y manda la galería entera con el orden resultante.
   *
   * <p>Un puesto a la vez, con botones, y no arrastrando: el arrastre no existe para quien navega
   * con teclado, y aquí se mueven cuatro fotos, no cuarenta. El botón del extremo no se
   * deshabilita —un control deshabilitado no es enfocable y desaparece para un lector de
   * pantalla—: sencillamente no está, porque en el extremo no hay ningún movimiento que ofrecer.
   */
  protected mover(imagen: ImagenDeGaleriaAdmin, desplazamiento: -1 | 1): void {
    const actual = this.galeria().map((i) => i.id);
    const desde = actual.indexOf(imagen.id);
    const hasta = desde + desplazamiento;
    if (desde < 0 || hasta < 0 || hasta >= actual.length) {
      return;
    }
    const pedido = [...actual];
    [pedido[desde], pedido[hasta]] = [pedido[hasta], pedido[desde]];

    this.errorReordenar.set(null);
    this.aviso.set(null);
    this.mutacionReordenarGaleria.mutate(
      { productoId: this.id(), imagenIds: pedido },
      {
        onSuccess: () => {
          this.aviso.set('admin.productos.editar.galeria.reordenada');
          // El foco sigue a la imagen que se movió, no al sitio donde estaba el botón: si se
          // quedara quieto, pulsar "subir" dos veces movería dos imágenes distintas.
          this.enfocarDespuesDePintar(() => this.botonDeMoverDe(imagen.id, desplazamiento));
        },
        onError: (error) =>
          this.errorReordenar.set(
            mensajeDeError(error, this.transloco, 'admin.productos.editar.galeria.errorReordenar'),
          ),
      },
    );
  }

  /**
   * El botón que movió la imagen, ya en su fila nueva. Puede no existir: si la imagen llegó a un
   * extremo, ese botón desaparece, y entonces el foco va al que sigue teniendo sentido.
   */
  private botonDeMoverDe(imagenId: string, desplazamiento: -1 | 1): HTMLElement | null {
    const fila = this.filas().find((f) => f.nativeElement.dataset['imagenId'] === imagenId);
    if (!fila) {
      return null;
    }
    // Hasta el `<button>` de dentro: el atributo cae en el host de `ts-boton`, que no es
    // enfocable.
    const direccion = desplazamiento === -1 ? 'subir' : 'bajar';
    return (
      fila.nativeElement.querySelector<HTMLElement>(`[data-mover="${direccion}"] button`) ??
      fila.nativeElement.querySelector<HTMLElement>('[data-mover] button')
    );
  }

  protected subirImagenPrincipal(): void {
    const archivo = this.archivoSeleccionado();
    if (!archivo || !this.dimensionesArchivo || this.formularioImagen.invalid) {
      this.formularioImagen.markAllAsTouched();
      return;
    }
    this.errorImagen.set(null);

    const { altEs, altEn } = this.formularioImagen.getRawValue();
    this.mutacionImagen.mutate(
      {
        productoId: this.id(),
        archivo,
        ancho: this.dimensionesArchivo.ancho,
        alto: this.dimensionesArchivo.alto,
        altEs,
        altEn,
      },
      {
        onSuccess: () => {
          this.limpiarPrevisualizacion();
          this.archivoSeleccionado.set(null);
          this.dimensionesArchivo = null;
          this.formularioImagen.reset();
        },
        onError: () =>
          this.errorImagen.set(
            this.transloco.translate('admin.productos.editar.imagenPrincipal.error'),
          ),
      },
    );
  }
}
