import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { usarOpcionesFiltro } from '../../../../catalogo/application/listar-opciones-filtro.consulta';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { TsPaginaFormulario } from '../../../../../shared/ui/pagina-formulario/ts-pagina-formulario';
import { TsCampo } from '../../../../../shared/ui/campo/ts-campo';
import { TsMigas } from '../../../../../shared/ts-migas/ts-migas';
import { usarTraductor } from '../../../../../core/i18n/traductor';
import { hojasConRuta } from '../../../../catalogo/domain/arbol-categorias';
import { claveDeLinea } from '../../../../catalogo/domain/filtro-productos.model';
import { OpcionSelect, TsSelect } from '../../../../../shared/ui/select/ts-select';
import { TsSelectControl } from '../../../../../shared/ui/select/ts-select-control';
import { usarMigasAdmin } from '../../../migas-admin';
import { usarCrearProductoAdmin } from '../../application/crear-producto-admin.mutacion';

@Component({
  selector: 'app-crear-producto-admin',
  imports: [
    TsPaginaFormulario,
    ReactiveFormsModule,
    RouterLink,
    TranslocoPipe,
    TsBoton,
    TsCampo,
    TsMigas,
    TsSelect,
    TsSelectControl,
  ],
  templateUrl: './crear-producto-admin.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CrearProductoAdminPage {
  protected readonly migas = usarMigasAdmin([
    { clave: 'admin.productos.titulo', ruta: ['productos'] },
    { clave: 'admin.productos.crear.titulo' },
  ]);

  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);
  private readonly opciones = usarOpcionesFiltro();
  private readonly mutacion = usarCrearProductoAdmin();

  protected readonly error = signal<string | null>(null);

  protected readonly form = new FormGroup({
    nombre: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    descripcion: new FormControl('', { nonNullable: true }),
    marcaId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    categoriaId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  // valueChanges, no statusChanges: mismo motivo que registro-cliente.page.ts — el estado del
  // formulario puede quedarse INVALID de punta a punta mientras cambia, así que statusChanges no
  // emitiría de nuevo tras la carga inicial de las opciones.
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

  /**
   * Solo las **hojas** del árbol, etiquetadas con su ruta ("Ropa › Dama › Camisas").
   *
   * Las hojas, porque un producto no cuelga de una rama: si "Camisas" tuviera productos y también
   * subcategorías, "lo que hay en Camisas" tendría dos respuestas distintas. Lo rechaza el backend
   * (`CategoriaNoEsHojaException`) y ofrecerlo aquí sería proponer lo que se va a rechazar.
   *
   * Y la ruta, porque sin ella el desplegable tiene entradas que no se distinguen: "Busos" aparece
   * bajo Dama y bajo Caballero, y "Dama" en tres líneas.
   */
  private readonly traducir = usarTraductor();

  protected readonly opcionesCategoria = computed<OpcionSelect[]>(() => {
    const traducir = this.traducir();
    return hojasConRuta(this.opciones.categorias.data() ?? [], (linea) =>
      traducir(claveDeLinea(linea)),
    ).map((hoja) => ({ valor: hoja.categoria.id, etiqueta: hoja.ruta }));
  });

  protected enviar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      // Se dice qué falta en vez de deshabilitar el botón, que es lo que este mismo panel ya hace
      // en marcas, medidas y existencias: un `<button disabled>` sale del orden de tabulación y
      // quien navega con teclado no encuentra el botón ni se entera de por qué no pasa nada.
      this.error.set(this.transloco.translate('admin.productos.crear.faltanCampos'));
      return;
    }
    this.error.set(null);

    const valores = this.form.getRawValue();
    this.mutacion.mutate(
      {
        nombre: valores.nombre,
        descripcion: valores.descripcion,
        marcaId: valores.marcaId,
        categoriaId: valores.categoriaId,
      },
      {
        onSuccess: () =>
          void this.router.navigate(['/' + this.transloco.activeLang(), 'admin', 'productos']),
        onError: () => this.error.set(this.transloco.translate('admin.productos.crear.error')),
      },
    );
  }
}
