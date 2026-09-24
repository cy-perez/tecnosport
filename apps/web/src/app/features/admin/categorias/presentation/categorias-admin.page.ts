import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { TsBoton } from '../../../../shared/ui/boton/ts-boton';
import { TsCampo } from '../../../../shared/ui/campo/ts-campo';
import { TsEsqueleto } from '../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsMigas } from '../../../../shared/ts-migas/ts-migas';
import { OpcionSelect } from '../../../../shared/ui/select/ts-select';
import { TsSelect } from '../../../../shared/ui/select/ts-select';
import { TsSelectControl } from '../../../../shared/ui/select/ts-select-control';
import { agruparPorLinea, NodoCategoria } from '../../../catalogo/domain/arbol-categorias';
import { LINEAS } from '../../../catalogo/domain/filtro-productos.model';
import { Categoria } from '../../../catalogo/domain/producto.model';
import { usarTraductor } from '../../../../core/i18n/traductor';
import { claveDeLinea } from '../../../catalogo/domain/filtro-productos.model';
import { usarMigasAdmin } from '../../migas-admin';
import { usarCategoriasAdmin } from '../application/listar-categorias-admin.consulta';
import {
  usarCrearCategoria,
  usarEditarCategoria,
  usarEliminarCategoria,
} from '../application/escribir-categoria.mutacion';
import { ResultadoEscritura } from '../domain/repositorio-categorias-admin.puerto';

/** Una fila de la lista: la categoría, y a qué profundidad se pinta. */
export interface FilaDeArbol {
  readonly categoria: Categoria;
  readonly nivel: number;
  readonly esHoja: boolean;
}

/**
 * El árbol de categorías del catálogo, administrado.
 *
 * <b>Esta pantalla no existía y el árbol era una migración.</b> Lo sigue siendo el árbol inicial
 * —las treinta de `V63`, que toda instalación necesita—, y esto es lo que aquel razonamiento no
 * cubría: la subcategoría que pide el proveedor del lunes. Mismo argumento que `MarcasAdminPage` y
 * `ADR-0047`.
 *
 * Una sola pantalla para las cuatro operaciones, y no cuatro: el árbol es lo que hay que ver para
 * decidir cualquiera de ellas —dónde cuelga, qué hay al lado, qué arrastra— y repartirlo obligaría
 * a pintarlo cuatro veces.
 *
 * Lo que la pantalla **no** hace es decidir las reglas: la profundidad, los ciclos, la hoja y el
 * "tiene productos" los defiende el backend, y aquí solo se traducen sus respuestas. Duplicar una
 * regla en el cliente es garantizar que un día digan cosas distintas.
 */
@Component({
  selector: 'app-categorias-admin',
  imports: [
    ReactiveFormsModule,
    TranslocoPipe,
    TsBoton,
    TsCampo,
    TsEsqueleto,
    TsMigas,
    TsSelect,
    TsSelectControl,
  ],
  templateUrl: './categorias-admin.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CategoriasAdminPage {
  protected readonly migas = usarMigasAdmin([{ clave: 'admin.categorias.titulo' }]);

  private readonly transloco = inject(TranslocoService);

  protected readonly consulta = usarCategoriasAdmin();
  private readonly creacion = usarCrearCategoria();
  private readonly edicion = usarEditarCategoria();
  private readonly borrado = usarEliminarCategoria();

  protected readonly categorias = computed<readonly Categoria[]>(() => this.consulta.data() ?? []);

  /**
   * El árbol aplanado a filas con su nivel. Se aplana aquí y no en la plantilla porque Angular no
   * tiene recursión de plantillas sin un componente aparte, y un componente para pintar una fila
   * sangrada es más ceremonia que el problema.
   */
  protected readonly filas = computed<readonly FilaDeArbol[]>(() => {
    const filas: FilaDeArbol[] = [];
    const recorrer = (nodo: NodoCategoria, nivel: number): void => {
      filas.push({ categoria: nodo.categoria, nivel, esHoja: nodo.hijas.length === 0 });
      for (const hija of nodo.hijas) {
        recorrer(hija, nivel + 1);
      }
    };
    for (const rama of agruparPorLinea(this.categorias())) {
      for (const nodo of rama.nodos) {
        recorrer(nodo, 0);
      }
    }
    return filas;
  });

  /** Las filas agrupadas por línea, que es como se pinta: un encabezado por línea. */
  protected readonly ramas = computed(() =>
    agruparPorLinea(this.categorias()).map((rama) => ({
      linea: rama.linea,
      filas: this.filas().filter((fila) => fila.categoria.linea === rama.linea),
    })),
  );

  /**
   * `usarTraductor` y no `transloco.translate()` dentro del `computed`: ese no lee ninguna señal,
   * así que se evalúa una sola vez —antes de que el diccionario llegue por HTTP— y deja la clave
   * cruda en pantalla para siempre (`apps/web/CLAUDE.md`).
   */
  private readonly traducir = usarTraductor();

  protected readonly opcionesLinea = computed<OpcionSelect[]>(() => {
    const traducir = this.traducir();
    return LINEAS.map((linea) => ({ valor: linea, etiqueta: traducir(claveDeLinea(linea)) }));
  });

  /**
   * Las categorías que pueden ser madre: solo las de primer nivel, y solo las que no tienen
   * productos. Es la misma regla que el backend defiende, y ofrecerla aquí no la duplica —evita
   * proponer lo que se va a rechazar—. Si las dos se separan, manda el backend.
   */
  protected readonly opcionesPadre = computed<OpcionSelect[]>(() => {
    const traducir = this.traducir();
    return this.categorias()
      .filter((categoria) => categoria.padreId === null)
      .map((categoria) => ({
        valor: categoria.id,
        etiqueta: `${traducir(claveDeLinea(categoria.linea))} › ${categoria.nombre}`,
      }));
  });

  /** La categoría en edición, o `null` si el formulario está en modo alta. */
  protected readonly editando = signal<Categoria | null>(null);

  /**
   * El id de la categoría cuyo borrado está pendiente de confirmar.
   *
   * Confirmación en dos toques y no un diálogo modal: el backend ya rechaza borrar una categoría
   * con hijas o con productos, así que lo único que se puede perder aquí de un clic es una hoja
   * vacía — y para eso, un botón que cambia de texto y exige un segundo toque cuesta menos que
   * abrir un modal, atrapar el foco y devolverlo. Lo que sí hace falta es que no sea silencioso.
   */
  protected readonly porBorrar = signal<string | null>(null);

  protected readonly aviso = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  protected readonly enviando = computed(
    () => this.creacion.isPending() || this.edicion.isPending() || this.borrado.isPending(),
  );

  protected readonly form = new FormGroup({
    nombre: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    slug: new FormControl('', { nonNullable: true }),
    linea: new FormControl('', { nonNullable: true }),
    padreId: new FormControl('', { nonNullable: true }),
  });

  protected editar(categoria: Categoria): void {
    this.editando.set(categoria);
    this.aviso.set(null);
    this.error.set(null);
    this.form.setValue({
      nombre: categoria.nombre,
      slug: categoria.slug,
      linea: categoria.padreId === null ? categoria.linea : '',
      padreId: categoria.padreId ?? '',
    });
  }

  protected cancelar(): void {
    this.editando.set(null);
    this.porBorrar.set(null);
    this.form.reset();
  }

  protected enviar(): void {
    // Guarda de reentrada en vez de deshabilitar el botón: un botón que se apaga bajo el dedo manda
    // el foco a `<body>`. Mismo criterio que `MarcasAdminPage`.
    if (this.enviando()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.error.set(this.transloco.translate('admin.categorias.faltaNombre'));
      return;
    }

    const valores = this.form.getRawValue();
    const padreId = valores.padreId === '' ? undefined : valores.padreId;
    const linea = padreId === undefined && valores.linea !== '' ? valores.linea : undefined;

    if (padreId === undefined && linea === undefined) {
      this.error.set(this.transloco.translate('admin.categorias.faltaLinea'));
      return;
    }

    this.error.set(null);
    this.aviso.set(null);

    const enEdicion = this.editando();
    const slug = valores.slug === '' ? undefined : valores.slug;

    if (enEdicion) {
      this.edicion.mutate(
        { id: enEdicion.id, nombre: valores.nombre, slug, linea, padreId },
        {
          onSuccess: (resultado) => this.alResponder(resultado, 'editada'),
          onError: () => this.error.set(this.transloco.translate('admin.categorias.error')),
        },
      );
      return;
    }

    this.creacion.mutate(
      { nombre: valores.nombre, slug, linea, padreId },
      {
        onSuccess: (resultado) => this.alResponder(resultado, 'creada'),
        onError: () => this.error.set(this.transloco.translate('admin.categorias.error')),
      },
    );
  }

  protected borrar(categoria: Categoria): void {
    if (this.enviando()) {
      return;
    }
    // Primer toque: se pide confirmación y no se borra nada.
    if (this.porBorrar() !== categoria.id) {
      this.porBorrar.set(categoria.id);
      this.error.set(null);
      this.aviso.set(null);
      return;
    }

    this.porBorrar.set(null);
    this.error.set(null);
    this.aviso.set(null);

    this.borrado.mutate(categoria.id, {
      onSuccess: (resultado) => {
        if (resultado.tipo !== 'OK') {
          this.error.set(this.mensajeDeRechazo(resultado));
          return;
        }
        if (this.editando()?.id === categoria.id) {
          this.cancelar();
        }
        this.aviso.set(
          this.transloco.translate('admin.categorias.borrada', { nombre: categoria.nombre }),
        );
      },
      onError: () => this.error.set(this.transloco.translate('admin.categorias.error')),
    });
  }

  private alResponder(resultado: ResultadoEscritura, clave: 'creada' | 'editada'): void {
    if (resultado.tipo !== 'OK') {
      this.error.set(this.mensajeDeRechazo(resultado));
      return;
    }
    this.aviso.set(
      this.transloco.translate(`admin.categorias.${clave}`, {
        nombre: resultado.categoria?.nombre ?? '',
      }),
    );
    this.cancelar();
  }

  /**
   * Cada rechazo dice qué hacer, no solo que no se pudo: "mueve primero sus productos" es
   * accionable y "error al guardar" no. Es para lo que el adaptador los traduce a un tipo con
   * nombre en vez de dejar pasar el código HTTP.
   */
  private mensajeDeRechazo(resultado: ResultadoEscritura): string {
    switch (resultado.tipo) {
      case 'SLUG_REPETIDO':
        return this.transloco.translate('admin.categorias.slugRepetido');
      case 'TIENE_PRODUCTOS':
        return this.transloco.translate('admin.categorias.tieneProductos');
      case 'TIENE_SUBCATEGORIAS':
        return this.transloco.translate('admin.categorias.tieneSubcategorias');
      case 'DEMASIADO_PROFUNDA':
        return this.transloco.translate('admin.categorias.demasiadoProfunda');
      case 'CICLO':
        return this.transloco.translate('admin.categorias.ciclo');
      default:
        return this.transloco.translate('admin.categorias.error');
    }
  }
}
