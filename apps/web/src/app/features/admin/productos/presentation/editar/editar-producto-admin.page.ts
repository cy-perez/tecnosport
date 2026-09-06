import { isPlatformBrowser } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  PLATFORM_ID,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { usarOpcionesFiltro } from '../../../../catalogo/application/listar-opciones-filtro.consulta';
import { TsBoton } from '../../../../../shared/ts-boton/ts-boton';
import { TsCampo } from '../../../../../shared/ts-campo/ts-campo';
import { TsEsqueleto } from '../../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsMigas } from '../../../../../shared/ts-migas/ts-migas';
import { OpcionSelect, TsSelect } from '../../../../../shared/ts-select/ts-select';
import { usarMigasAdmin } from '../../../migas-admin';
import { usarEditarProductoAdmin } from '../../application/editar-producto-admin.mutacion';
import { usarSubirImagenPrincipalAdmin } from '../../application/subir-imagen-principal-admin.mutacion';
import { usarVerProductoAdmin } from '../../application/ver-producto-admin.consulta';

const TIPOS_DE_IMAGEN_SOPORTADOS = ['image/jpeg', 'image/png', 'image/webp'];

@Component({
  selector: 'app-editar-producto-admin',
  imports: [ReactiveFormsModule, RouterLink, TranslocoPipe, TsBoton, TsCampo, TsEsqueleto, TsMigas, TsSelect],
  templateUrl: './editar-producto-admin.page.html',
  styleUrl: './editar-producto-admin.page.scss',
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
  private readonly esNavegador = isPlatformBrowser(inject(PLATFORM_ID));

  private readonly paramMap = toSignal(this.route.paramMap, { initialValue: this.route.snapshot.paramMap });
  protected readonly id = computed(() => this.paramMap().get('id') ?? '');

  protected readonly consulta = usarVerProductoAdmin(this.id);

  protected readonly error = signal<string | null>(null);
  private prefilled = false;

  protected readonly form = new FormGroup({
    nombre: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    descripcion: new FormControl('', { nonNullable: true }),
    marcaId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    categoriaId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  private readonly valorFormulario = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });
  protected readonly formularioInvalido = computed(() => {
    this.valorFormulario();
    return this.form.invalid;
  });

  protected readonly enviando = computed(() => this.mutacion.isPending());

  protected readonly opcionesMarca = computed<OpcionSelect[]>(() =>
    (this.opciones.marcas.data() ?? []).map((marca) => ({ valor: marca.id, etiqueta: marca.nombre })),
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
        onSuccess: () => void this.router.navigate(['/' + this.transloco.activeLang(), 'admin', 'productos']),
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
      this.errorImagen.set(this.transloco.translate('admin.productos.editar.imagenPrincipal.tipoNoSoportado'));
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
      this.errorImagen.set(this.transloco.translate('admin.productos.editar.imagenPrincipal.error'));
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
        onError: () => this.errorImagen.set(this.transloco.translate('admin.productos.editar.imagenPrincipal.error')),
      },
    );
  }
}
