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
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { mensajeDeError } from '../../../../../core/errores/mensaje-de-error';
import { usarIdiomaActivo, usarTraductor } from '../../../../../core/i18n/traductor';
import { usarFoco } from '../../../../../shared/foco/foco';
import { formatearPrecio } from '../../../../../shared/ts-precio/formato-precio';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { TsCampo } from '../../../../../shared/ui/campo/ts-campo';
import { TsCheckbox } from '../../../../../shared/ui/checkbox/ts-checkbox';
import { OpcionSelect, TsSelect } from '../../../../../shared/ui/select/ts-select';
import { TsSelectControl } from '../../../../../shared/ui/select/ts-select-control';
import { TsEsqueleto } from '../../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsMigas } from '../../../../../shared/ts-migas/ts-migas';
import { usarOpcionesDeFormulario } from '../../../../catalogo/application/listar-opciones-filtro.consulta';
import { hojasConRuta } from '../../../../catalogo/domain/arbol-categorias';
import { claveDeLinea } from '../../../../catalogo/domain/filtro-productos.model';
import { clasesDeEstadoBorrador } from '../../../borradores/presentation/estado-borrador';
import { usarMigasAdmin } from '../../../migas-admin';
import { usarProveedoresAdmin } from '../../../proveedores/application/listar-proveedores.consulta';
import { usarVerBorradorTecnologia } from '../../application/borradores-tecnologia.consulta';
import {
  usarAprobarBorradorTecnologia,
  usarElegirConfiguraciones,
  usarRechazarBorradorTecnologia,
} from '../../application/decidir-borrador-tecnologia.mutacion';
import {
  BorradorTecnologia,
  borradorTecnologiaEditable,
  coloresIniciales,
  completaUnProductoExistente,
  ConfiguracionTecnologia,
  EleccionDeConfiguracion,
  mismoColor,
  precioEscrito,
  precioInicial,
  problemaDeAprobacion,
  separarColoresEscritos,
} from '../../domain/borrador-tecnologia.model';

/** «Samsung», «SAMSUNG» y «samsung» son la misma marca; «Celulares» y «celulares», la misma hoja. */
function sinTildes(texto: string): string {
  return texto.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim();
}

/** El campo de precio y, sin paleta, el de colores escritos a mano, de una configuración. */
interface ControlesDeConfiguracion {
  readonly precio: FormControl<string>;
  readonly coloresEscritos: FormControl<string>;
}

/**
 * La revisión de un modelo de tecnología: qué colores de la paleta oficial se venden de cada
 * configuración y a qué precio, y la marca y la categoría del catálogo si el modelo es nuevo.
 *
 * <p>Los colores arrancan con lo que sugiere la lista y nada más: vender un color que el proveedor
 * no dijo tener es lo que esta revisión existe para evitar. El precio arranca en el de mercado,
 * que es un punto de partida y no una recomendación. Una configuración sin colores no se vende.
 *
 * <p>Aprobar guarda primero lo que está en pantalla. El producto nace en borrador: sus fotos salen
 * de la carpeta de fichas del modelo.
 */
@Component({
  selector: 'app-detalle-tecnologia-admin',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    TranslocoPipe,
    TsBoton,
    TsCampo,
    TsCheckbox,
    TsEsqueleto,
    TsMigas,
    TsSelect,
    TsSelectControl,
  ],
  templateUrl: './detalle-tecnologia-admin.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DetalleTecnologiaAdminPage {
  private readonly route = inject(ActivatedRoute);
  private readonly transloco = inject(TranslocoService);
  private readonly traducir = usarTraductor();

  private readonly paramMap = toSignal(this.route.paramMap, {
    initialValue: this.route.snapshot.paramMap,
  });
  protected readonly id = computed(() => this.paramMap().get('id') ?? '');

  protected readonly migas = usarMigasAdmin([
    { clave: 'admin.tecnologia.titulo', ruta: ['tecnologia'] },
    { clave: 'admin.tecnologia.detalle.miga' },
  ]);

  protected readonly consulta = usarVerBorradorTecnologia(this.id);
  private readonly proveedores = usarProveedoresAdmin();
  private readonly opciones = usarOpcionesDeFormulario();
  private readonly elegir = usarElegirConfiguraciones();
  private readonly aprobar = usarAprobarBorradorTecnologia();
  private readonly rechazar = usarRechazarBorradorTecnologia();

  protected readonly idioma = usarIdiomaActivo();
  protected readonly borrador = computed<BorradorTecnologia | null>(
    () => this.consulta.data() ?? null,
  );
  protected readonly editable = computed(() => {
    const borrador = this.borrador();
    return borrador !== null && borradorTecnologiaEditable(borrador);
  });
  protected readonly completaExistente = computed(() => {
    const borrador = this.borrador();
    return borrador !== null && completaUnProductoExistente(borrador);
  });
  protected readonly nombreDelProveedor = computed(() => {
    const borrador = this.borrador();
    return (
      (this.proveedores.data() ?? []).find((p) => p.id === borrador?.proveedorId)?.nombre ?? ''
    );
  });

  /** Los colores marcados de cada configuración, por SKU. */
  protected readonly colores = signal<ReadonlyMap<string, readonly string[]>>(new Map());
  private readonly controles = new Map<string, ControlesDeConfiguracion>();
  /** Los SKU que ya tienen controles: una señal, para que la plantilla espere a que existan. */
  private readonly conControles = signal<ReadonlySet<string>>(new Set());
  private readonly inicializadoPara = signal<string | null>(null);
  /**
   * Las configuraciones se pintan cuando sus controles existen, no antes. Una importación puede
   * añadir una con la página abierta, y la revalidación la trae antes de que el `effect` le cree
   * los suyos.
   */
  protected readonly listo = computed(() => {
    const borrador = this.borrador();
    const con = this.conControles();
    return (
      borrador !== null &&
      this.inicializadoPara() === borrador.id &&
      borrador.configuraciones.every((c) => con.has(c.sku))
    );
  });
  /** El error del precio de cada configuración, por SKU: se pinta en su campo. */
  protected readonly erroresDePrecio = signal<ReadonlyMap<string, string>>(new Map());

  protected readonly marca = new FormControl('', { nonNullable: true });
  protected readonly categoria = new FormControl('', { nonNullable: true });
  protected readonly motivoRechazo = new FormControl('', { nonNullable: true });

  protected readonly guardando = computed(() => this.elegir.isPending());
  protected readonly aprobando = computed(() => this.aprobar.isPending());
  protected readonly rechazando = computed(() => this.rechazar.isPending());
  private readonly ocupado = computed(
    () => this.guardando() || this.aprobando() || this.rechazando(),
  );

  protected readonly avisoGuardado = signal(false);
  protected readonly errorGuardar = signal<string | null>(null);
  protected readonly errorAprobar = signal<string | null>(null);
  protected readonly errorRechazo = signal<string | null>(null);
  /** El producto creado o completado, para enlazarlo desde el aviso. */
  protected readonly aprobado = signal<string | null>(null);
  protected readonly rechazado = signal(false);

  private readonly enfocarDespuesDePintar = usarFoco();
  private readonly avisoDecisionRef = viewChild<ElementRef<HTMLElement>>('avisoDecisionRef');

  protected readonly opcionesMarca = computed<OpcionSelect[]>(() =>
    (this.opciones.marcas.data() ?? []).map((marca) => ({
      valor: marca.id,
      etiqueta: marca.nombre,
    })),
  );

  /** Solo las hojas, con su ruta: mismo criterio que el alta de producto. */
  protected readonly opcionesCategoria = computed<OpcionSelect[]>(() => {
    const traducir = this.traducir();
    return hojasConRuta(this.opciones.categorias.data() ?? [], (linea) =>
      traducir(claveDeLinea(linea)),
    ).map((hoja) => ({ valor: hoja.categoria.id, etiqueta: hoja.ruta }));
  });

  constructor() {
    // Todo de nuevo solo al cargar otro borrador, no en cada revalidación: volver a poner lo del
    // servidor borraría lo que alguien acaba de marcar sin guardar (apps/web/CLAUDE.md). En una
    // revalidación solo se completan las configuraciones que no tenían controles.
    effect(() => {
      const borrador = this.borrador();
      if (borrador === null) {
        return;
      }
      untracked(() => {
        if (this.inicializadoPara() !== borrador.id) {
          this.controles.clear();
          this.colores.set(new Map());
          this.inicializadoPara.set(borrador.id);
        }
        this.completar(borrador);
      });
    });

    // La marca y la categoría que sugiere la skill, si el catálogo tiene una con ese nombre y nadie
    // eligió otra todavía.
    effect(() => {
      const borrador = this.borrador();
      const marcas = this.opciones.marcas.data() ?? [];
      const categorias = this.opciones.categorias.data() ?? [];
      untracked(() => {
        if (borrador?.marcaSugerida && this.marca.value === '') {
          const sugerida = sinTildes(borrador.marcaSugerida);
          const marca = marcas.find((m) => sinTildes(m.nombre) === sugerida);
          if (marca) {
            this.marca.setValue(marca.id);
          }
        }
        if (borrador?.categoriaSugerida && this.categoria.value === '') {
          // Entre las hojas: puede haber una rama y una hoja con el mismo nombre, y solo la hoja
          // se puede elegir.
          const sugerida = sinTildes(borrador.categoriaSugerida);
          const hojas = new Set(this.opcionesCategoria().map((o) => o.valor));
          const categoria = categorias.find(
            (c) =>
              hojas.has(c.id) &&
              (sinTildes(c.nombre) === sugerida || sinTildes(c.slug) === sugerida),
          );
          if (categoria) {
            this.categoria.setValue(categoria.id);
          }
        }
      });
    });
  }

  /** Crea los controles de las configuraciones que todavía no los tienen; las demás no se tocan. */
  private completar(borrador: BorradorTecnologia): void {
    const faltan = borrador.configuraciones.filter((c) => !this.controles.has(c.sku));
    if (faltan.length === 0) {
      return;
    }
    const colores = new Map(this.colores());
    for (const configuracion of faltan) {
      const iniciales = coloresIniciales(configuracion, borrador.paleta);
      colores.set(configuracion.sku, iniciales);
      const precio = precioInicial(configuracion);
      this.controles.set(configuracion.sku, {
        precio: new FormControl(precio === null ? '' : String(precio), { nonNullable: true }),
        coloresEscritos: new FormControl(iniciales.join(', '), { nonNullable: true }),
      });
    }
    this.colores.set(colores);
    this.conControles.set(new Set(this.controles.keys()));
  }

  protected controlesDe(sku: string): ControlesDeConfiguracion {
    const controles = this.controles.get(sku);
    if (!controles) {
      throw new Error('Sin controles para la configuración ' + sku);
    }
    return controles;
  }

  protected marcado(sku: string, color: string): boolean {
    return this.colores().get(sku)?.includes(color) ?? false;
  }

  protected marcar(sku: string, color: string, dentro: boolean): void {
    const borrador = this.borrador();
    if (!borrador) {
      return;
    }
    const actuales = this.colores().get(sku) ?? [];
    const siguientes = dentro ? [...actuales, color] : actuales.filter((c) => c !== color);
    // En el orden de la paleta, que es el de la marca, y no en el de los clics.
    const ordenados = borrador.paleta.filter((c) => siguientes.includes(c));
    const mapa = new Map(this.colores());
    mapa.set(sku, ordenados);
    this.colores.set(mapa);
  }

  protected precio(valor: number | null): string {
    return valor === null ? '-' : formatearPrecio(valor, 'COP', this.idioma());
  }

  protected ayudaDePrecio(configuracion: ConfiguracionTecnologia): string {
    return this.traducir()('admin.tecnologia.detalle.ayudaPrecio', {
      costo: this.precio(configuracion.costoProveedor),
      mercado: this.precio(configuracion.precioMercado),
    });
  }

  protected etiquetaEstado(borrador: BorradorTecnologia): string {
    return this.traducir()('admin.borradores.estados.' + borrador.estado);
  }

  protected clasesEstado(borrador: BorradorTecnologia): string {
    return clasesDeEstadoBorrador(borrador.estado);
  }

  protected errorDePrecio(sku: string): string | null {
    return this.erroresDePrecio().get(sku) ?? null;
  }

  /**
   * Lo que está en pantalla, o nulo si algún precio está mal escrito: entonces el error se pinta en
   * su campo y no se manda nada. Mandarlo sin precio borraba en silencio el que ya estaba guardado.
   */
  private elecciones(): EleccionDeConfiguracion[] | null {
    const borrador = this.borrador();
    if (!borrador) {
      return [];
    }
    const errores = new Map<string, string>();
    const elecciones = borrador.configuraciones.map((configuracion) => {
      const controles = this.controlesDe(configuracion.sku);
      // Con paleta, solo lo que está en ella: un color que salió no se ve para desmarcarlo.
      const colores =
        borrador.paleta.length === 0
          ? separarColoresEscritos(controles.coloresEscritos.value)
          : (this.colores().get(configuracion.sku) ?? []).filter((c) =>
              borrador.paleta.some((p) => mismoColor(p, c)),
            );
      const precio = precioEscrito(controles.precio.value);
      if (precio === 'ilegible') {
        errores.set(
          configuracion.sku,
          this.transloco.translate('admin.tecnologia.detalle.precioIlegible'),
        );
      }
      return {
        sku: configuracion.sku,
        colores,
        precioVenta: precio === 'ilegible' ? null : precio,
      };
    });
    this.erroresDePrecio.set(errores);
    return errores.size > 0 ? null : elecciones;
  }

  protected guardar(): void {
    if (this.ocupado()) {
      return;
    }
    this.avisoGuardado.set(false);
    this.errorGuardar.set(null);
    const elecciones = this.elecciones();
    if (elecciones === null) {
      this.errorGuardar.set(this.transloco.translate('admin.tecnologia.detalle.precioIlegible'));
      return;
    }
    this.elegir.mutate(
      { id: this.id(), elecciones },
      {
        onSuccess: () => this.avisoGuardado.set(true),
        onError: (error: unknown) =>
          this.errorGuardar.set(
            mensajeDeError(error, this.transloco, 'admin.tecnologia.detalle.errorGuardar'),
          ),
      },
    );
  }

  protected aprobarBorrador(): void {
    if (this.ocupado()) {
      return;
    }
    const elecciones = this.elecciones();
    if (elecciones === null) {
      this.errorAprobar.set(this.transloco.translate('admin.tecnologia.detalle.precioIlegible'));
      return;
    }
    const problema = problemaDeAprobacion(elecciones, this.borrador()?.configuraciones ?? []);
    if (problema !== null) {
      this.errorAprobar.set(this.transloco.translate('admin.tecnologia.aprobar.' + problema));
      return;
    }
    const nuevo = !this.completaExistente();
    if (nuevo && (this.marca.value === '' || this.categoria.value === '')) {
      this.errorAprobar.set(
        this.transloco.translate('admin.tecnologia.aprobar.faltanMarcaYCategoria'),
      );
      return;
    }
    this.errorAprobar.set(null);

    this.aprobar.mutate(
      {
        id: this.id(),
        elecciones,
        aprobacion: {
          marcaId: nuevo ? this.marca.value : null,
          categoriaId: nuevo ? this.categoria.value : null,
        },
      },
      {
        onSuccess: (productoId) => {
          this.aprobado.set(productoId);
          this.enfocarDespuesDePintar(() => this.avisoDecisionRef()?.nativeElement);
        },
        onError: (error: unknown) =>
          this.errorAprobar.set(
            mensajeDeError(error, this.transloco, 'admin.tecnologia.aprobar.error'),
          ),
      },
    );
  }

  protected rechazarBorrador(): void {
    if (this.ocupado()) {
      return;
    }
    const motivo = this.motivoRechazo.value.trim();
    if (!motivo) {
      this.errorRechazo.set(this.transloco.translate('admin.tecnologia.rechazar.faltaMotivo'));
      return;
    }
    this.errorRechazo.set(null);

    this.rechazar.mutate(
      { id: this.id(), motivo },
      {
        onSuccess: () => {
          this.rechazado.set(true);
          this.enfocarDespuesDePintar(() => this.avisoDecisionRef()?.nativeElement);
        },
        onError: (error: unknown) =>
          this.errorRechazo.set(
            mensajeDeError(error, this.transloco, 'admin.tecnologia.rechazar.error'),
          ),
      },
    );
  }
}
