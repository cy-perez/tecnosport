import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { usarTraductor } from '../../../../core/i18n/traductor';
import {
  migasJsonLd,
  productoJsonLd,
  EslabonDeRuta,
} from '../../../../core/seo/datos-estructurados';
import {
  rutaCanonica,
  urlAbsoluta,
  urlDeRecursoAbsoluta,
} from '../../../../core/seo/enlaces-alternativos';
import { MetadatosPagina } from '../../../../core/seo/metadatos.model';
import { origenPublico } from '../../../../core/seo/origen-publico';
import { resumirDescripcion } from '../../../../core/seo/resumen-descripcion';
import { usarDatosEstructurados, usarMetadatos } from '../../../../core/seo/usar-metadatos';
import { AltoEnVariable } from '../../../../shared/alto-en-variable/alto-en-variable';
import { TsBoton } from '../../../../shared/ui/boton/ts-boton';
import { TsEsqueleto } from '../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsEtiquetaStock } from '../etiqueta-stock/ts-etiqueta-stock';
import { TsGaleria } from '../galeria/ts-galeria';
import { Miga, TsMigas } from '../../../../shared/ts-migas/ts-migas';
import { TsPrecio } from '../../../../shared/ts-precio/ts-precio';
import { TsSelectorVariante } from '../selector-variante/ts-selector-variante';
import { TsVisor360 } from '../../../../shared/ts-visor-360/ts-visor-360';
import { usarFichaProducto } from '../../application/buscar-ficha-producto.consulta';
import { CarritoStore } from '../../../carrito/application/carrito.store';
import { hayExistencia, Imagen } from '../../domain/producto.model';
import { usarPaletaDeColores } from '../../application/listar-paleta-colores.consulta';
import { usarIdiomaActivo } from '../../../../core/i18n/traductor';
import {
  coloresDe,
  detalleDeVariante,
  ejeDeColor,
  ejesDeAtributos,
  esEjeDeTalla,
  imagenDelColor,
  indiceDeLaPrimeraDelColor,
  nombreDeColor,
  opcionDisponible,
  Seleccion,
  seleccionAlElegir,
  seleccionDeVariante,
  soloTallasElegibles,
  tallaUnicaDe,
  variantePorDefecto,
  varianteSeleccionada,
} from '../../domain/seleccion-variante';

@Component({
  selector: 'app-ficha',
  imports: [
    AltoEnVariable,
    TranslocoPipe,
    TsGaleria,
    TsSelectorVariante,
    TsPrecio,
    TsEtiquetaStock,
    TsEsqueleto,
    TsBoton,
    TsMigas,
    TsVisor360,
  ],
  templateUrl: './ficha.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FichaPage {
  private readonly route = inject(ActivatedRoute);
  private readonly transloco = inject(TranslocoService);
  private readonly traducir = usarTraductor();
  protected readonly carrito = inject(CarritoStore);

  /**
   * Lo que pasó la última vez que se pulsó "Agregar al carrito", para la región viva de la barra de
   * compra. Antes no se decía nada: el botón se deshabilitaba mientras agregaba —el foco caía en
   * `<body>`— y al terminar no había ni un "listo" ni un "falló", y el fallo se tragaba con `void`.
   * El nombre se guarda aquí y no se lee del producto al pintar: si el anuncio es por la camiseta,
   * dice camiseta aunque la pantalla ya enseñe otra cosa.
   */
  protected readonly resultadoAgregar = signal<
    { readonly tipo: 'agregado'; readonly nombre: string } | { readonly tipo: 'error' } | null
  >(null);
  /** Para el enlace de vuelta al catálogo cuando el producto no existe. */
  protected readonly idioma = this.transloco.activeLang;

  private readonly slug = toSignal(this.route.paramMap, {
    initialValue: this.route.snapshot.paramMap,
  });

  protected readonly consulta = usarFichaProducto(() => this.slug().get('slug') ?? '');

  protected readonly producto = computed(() => this.consulta.data());

  // `usarTraductor`, no `transloco.translate()` directo: dentro de un
  // `computed` ese no es reactivo y las etiquetas se quedarían en el idioma
  // con el que se creó el componente (apps/web/CLAUDE.md).
  protected readonly migas = computed<Miga[]>(() => {
    const traducir = this.traducir();
    const idioma = this.transloco.activeLang();
    return [
      { etiqueta: traducir('migas.portada'), enlace: ['/', idioma] },
      { etiqueta: traducir('migas.catalogo'), enlace: ['/', idioma, 'productos'] },
      { etiqueta: this.producto()?.nombre ?? '' },
    ];
  });

  /**
   * Las fotos de la ficha: la galería entera, y la principal delante <b>solo si retrata un
   * color</b>.
   *
   * <b>No se recortan por color, y antes sí.</b> La galería enseñaba solo las del tono elegido,
   * así que un pantalón con nueve fotos y una por color salía con <b>una</b> foto y sin tira de
   * miniaturas: nada decía que hubiera más.
   *
   * <b>Y la principal que «vale para todos los tonos» no entra.</b> Esa foto es la portada del
   * catálogo —la que la tarjeta de la rejilla usa de previsualización— y no retrata ninguna de
   * las prendas que se pueden elegir: metida en la tira es una miniatura que no corresponde a
   * ningún color y que al pulsarla no cambia nada de lo que se compra. Cuando sí lleva color es
   * otra cosa: entonces es la foto de ese tono y además abre la ficha, porque es la que quien
   * viene de la rejilla acaba de ver.
   */
  protected readonly imagenesGaleria = computed<Imagen[]>(() => {
    const producto = this.producto();
    if (!producto) {
      return [];
    }
    const principal = producto.imagenPrincipal;
    if (principal === null) {
      return [...producto.galeria];
    }
    // Y si la galería está vacía, la principal entra aunque sea genérica: una ficha sin una sola
    // foto no le sirve a nadie, y es el mismo respaldo que ya tenía el recorte por color. La
    // regla distingue la portada de las fotos de cada prenda cuando hay prendas que distinguir.
    const encabeza = principal.varianteId !== null || producto.galeria.length === 0;
    return [...(encabeza ? [principal] : []), ...producto.galeria];
  });

  /**
   * Si quien mira ya eligió un color con el dedo, o si lo que hay elegido es solo la variante por
   * defecto que puso la pantalla al cargar.
   *
   * La diferencia decide dónde abre la galería, y no es un matiz: al cargar siempre hay una
   * variante elegida —la primera disponible—, así que sin esto la ficha abriría en la foto de
   * ese color y no en la principal. Quien viene de la rejilla acaba de pulsar una tarjeta que
   * enseñaba la principal; abrir en otra foto se lee como haber entrado a otro producto.
   */
  private readonly colorElegidoAMano = signal(false);

  /**
   * En qué foto se para la galería: la primera del color que se eligió, o la principal mientras
   * nadie haya elegido ninguno. Elegir un color mueve la foto activa; no cambia cuántas hay.
   */
  protected readonly indiceEnLaGaleria = computed<number>(() => {
    const producto = this.producto();
    if (!producto || !this.colorElegidoAMano()) {
      return 0;
    }
    const eje = ejeDeColor(producto);
    return indiceDeLaPrimeraDelColor(
      producto,
      this.imagenesGaleria(),
      eje ? (this.seleccion()[eje] ?? null) : null,
    );
  });

  /** El visor recibe URL y nada más. Llegan ya ordenadas por `orden` desde el mapeador. */
  protected readonly fotogramas360 = computed<string[]>(() =>
    (this.producto()?.rotacion?.imagenes ?? []).map((fotograma) => fotograma.url),
  );

  /**
   * La talla única no es una elección: no sale en el selector, se dice al lado del nombre. El eje de
   * talla sigue el orden de la escala de la categoría y enseña solo las tallas que se pueden comprar
   * con lo demás elegido: cambia al cambiar de color.
   */
  protected readonly ejes = computed(() => {
    const producto = this.producto();
    if (!producto) {
      return [];
    }
    const unica = tallaUnicaDe(producto) !== null;
    const ejes = ejesDeAtributos(producto, producto.escalaTallas).filter(
      (eje) => !(unica && esEjeDeTalla(eje.nombre)),
    );
    return soloTallasElegibles(producto, ejes, this.seleccion());
  });

  protected readonly tallaUnica = computed(() => {
    const producto = this.producto();
    return producto ? tallaUnicaDe(producto) : null;
  });

  private readonly paleta = usarPaletaDeColores();
  private readonly idiomaActivo = usarIdiomaActivo();

  /** El nombre de cada color en el idioma de quien mira, para el selector. */
  protected readonly nombresDeColor = computed<Record<string, string>>(() => {
    const producto = this.producto();
    const paleta = this.paleta.data() ?? [];
    const idioma = this.idiomaActivo();
    return Object.fromEntries(
      (producto ? coloresDe(producto) : []).map((color) => [
        color.valor,
        nombreDeColor(color.valor, paleta, idioma),
      ]),
    );
  });

  /** Lo que se ve tachado: lo que con lo demás elegido no lleva a nada que se pueda comprar. */
  protected readonly noDisponibles = computed<Record<string, string[]>>(() => {
    const producto = this.producto();
    if (!producto) {
      return {};
    }
    const seleccion = this.seleccion();
    return Object.fromEntries(
      this.ejes().map((eje) => [
        eje.nombre,
        eje.opciones
          .filter(
            (opcion) =>
              !opcion.existe || !opcionDisponible(producto, seleccion, eje.nombre, opcion.valor),
          )
          .map((opcion) => opcion.valor),
      ]),
    );
  });

  protected readonly seleccion = signal<Seleccion>({});

  protected readonly varianteActiva = computed(() => {
    const producto = this.producto();
    return producto ? varianteSeleccionada(producto, this.seleccion()) : null;
  });

  /**
   * Un producto **distinto**, por su slug. No sirve depender de `producto()`: TanStack revalida en
   * segundo plano al volver a la pestaña, y cualquier cambio real —un precio, una existencia—
   * devuelve un objeto nuevo. Con eso, la variante que eligió el visitante se reiniciaba sola a la
   * de por defecto. La misma trampa que el visor 360 tiene con la identidad de su arreglo.
   */
  private readonly slugCargado = computed(() => this.producto()?.slug ?? null);

  /**
   * La única pantalla del sitio cuyo título y descripción salen de datos y no de
   * la ruta. Devuelve `null` mientras no hay producto —cargando, o slug que no
   * existe— y en ese rato manda el título genérico que declara `catalogo.routes.ts`.
   */
  private readonly metadatos = computed<MetadatosPagina | null>(() => {
    const producto = this.producto();
    if (!producto) {
      return null;
    }
    const traducir = this.traducir();
    return {
      titulo: traducir('catalogo.seo.ficha.titulo_con_nombre', { nombre: producto.nombre }),
      // `descripcion` es `not null default ''` en la base: un producto sin
      // descripción no es un error, es lo que hay hasta que alguien la escriba —
      // toda la siembra está así. Sin respaldo, esas fichas salen sin
      // `meta description` y el buscador se inventa el fragmento recortando la
      // página, que casi siempre queda peor. El respaldo no inventa nada: es una
      // plantilla traducida rellenada con el nombre y la marca reales.
      descripcion:
        resumirDescripcion(producto.descripcion) ||
        traducir('catalogo.seo.ficha.descripcion_respaldo', {
          nombre: producto.nombre,
          marca: producto.marca.nombre,
        }),
      indexable: true,
      // La vista previa antes que la imagen del sitio, y esta línea decía lo
      // contrario. Decía que servía "el original" por compatibilidad con los
      // previsualizadores — pero desde ADR-0056 el original **es** el AVIF, así
      // que ese razonamiento dejó de proteger nada: WhatsApp y Facebook no
      // negocian formatos y con AVIF no muestran imagen. `urlVistaPrevia` es el
      // JPEG que se sube justo para esto. Mientras una imagen no lo tenga, se
      // cae a la del sitio: una tarjeta de enlace sin foto es mejor que un
      // `og:image` vacío.
      imagen: producto.imagenPrincipal?.urlVistaPrevia ?? producto.imagenPrincipal?.url,
    };
  });

  /**
   * `Product` con `AggregateOffer`, más la ruta de migas.
   *
   * Los precios salen de `precioDesde` y del mayor de las variantes, que es exactamente lo que la
   * página muestra: si el JSON-LD dijera un precio que el visitante no ve al llegar, Google lo
   * marca como incoherente y con razón. La URL es la canónica, la misma que declara el `<head>`.
   */
  private readonly datosEstructurados = computed<object[]>(() => {
    const producto = this.producto();
    const metadatos = this.metadatos();
    if (!producto || !metadatos) {
      return [];
    }

    const origen = origenPublico();
    const url = urlAbsoluta(
      origen,
      rutaCanonica(`/${this.transloco.activeLang()}/productos/${producto.slug}`),
    );
    const precios = producto.variantes.map((variante) => variante.precio.valor);

    const eslabones: EslabonDeRuta[] = this.migas().map((miga) => ({
      etiqueta: miga.etiqueta,
      url: miga.enlace
        ? urlAbsoluta(origen, miga.enlace.join('/').replace(/^\/+/, '/'))
        : undefined,
    }));

    return [
      productoJsonLd({
        nombre: producto.nombre,
        descripcion: metadatos.descripcion,
        url,
        // Por la misma función que `og:image`: los dos los lee una máquina de fuera y no pueden
        // discrepar.
        imagen: urlDeRecursoAbsoluta(origen, metadatos.imagen) || undefined,
        marca: producto.marca.nombre,
        precioMinimo: precios.length > 0 ? Math.min(...precios) : null,
        precioMaximo: precios.length > 0 ? Math.max(...precios) : null,
        moneda: producto.variantes[0]?.precio.moneda ?? 'COP',
        variantes: producto.variantes.length,
        hayExistencia: hayExistencia(producto),
      }),
      migasJsonLd(eslabones),
    ].filter((objeto): objeto is object => objeto !== null);
  });

  constructor() {
    usarMetadatos(() => this.metadatos());
    usarDatosEstructurados(() => this.datosEstructurados());

    // Reinicia la selección a la variante por defecto solo al cargar otro producto (primer render,
    // o al navegar de una ficha a otra), nunca porque el mismo producto haya vuelto del servidor.
    effect(() => {
      if (!this.slugCargado()) {
        return;
      }
      // `untracked`: leer el producto aquí volvería a atar el efecto a su identidad, que es
      // justamente lo que se quiere evitar.
      const producto = untracked(() => this.producto());
      // Otro producto, otro anuncio: el "Agregado" del anterior ya no habla de lo que hay en
      // pantalla.
      untracked(() => this.resultadoAgregar.set(null));
      if (!producto) {
        return;
      }
      const variante = variantePorDefecto(producto);
      this.seleccion.set(variante ? seleccionDeVariante(variante) : {});
      // Otro producto, otra galería: vuelve a abrir en la principal.
      untracked(() => this.colorElegidoAMano.set(false));
    });
  }

  /**
   * El selector manda la selección con la opción pulsada; si esa combinación no existe, se salta a
   * la variante que la tenga y más se parezca a lo elegido (`seleccionAlElegir`).
   */
  protected cambiarSeleccion(seleccion: Seleccion): void {
    const producto = this.producto();
    const anterior = this.seleccion();
    const cambiado = Object.keys(seleccion).find((eje) => seleccion[eje] !== anterior[eje]);
    // Desde aquí la galería sigue al color: esto solo lo llama el selector, o sea una persona.
    this.colorElegidoAMano.set(true);
    if (!producto || !cambiado) {
      this.seleccion.set(seleccion);
      return;
    }
    this.seleccion.set(seleccionAlElegir(producto, anterior, cambiado, seleccion[cambiado]));
  }

  protected agregarAlCarrito(): void {
    const producto = this.producto();
    const variante = this.varianteActiva();
    // Guarda de reentrada en vez de deshabilitar el botón (`[ocupado]`): deshabilitarlo bajo el
    // dedo mandaba el foco a `<body>`. Y agotada no agrega: el botón la anuncia con
    // `aria-disabled`, pero sigue recibiendo el clic.
    if (!producto || !variante || !variante.disponible || this.carrito.agregando()) {
      return;
    }
    this.resultadoAgregar.set(null);
    // La foto del color que se lleva, no la principal: en el carrito se ve lo que eligió.
    const eje = ejeDeColor(producto);
    const imagen = imagenDelColor(producto, eje ? (this.seleccion()[eje] ?? null) : null);
    const detalle = detalleDeVariante(variante);
    this.carrito
      .agregarAlCarrito(variante.id, 1, {
        varianteId: variante.id,
        nombreProducto: producto.nombre,
        slugProducto: producto.slug,
        sku: variante.sku,
        imagenUrl: imagen?.url ?? null,
        precioValor: variante.precio.valor,
        precioMoneda: variante.precio.moneda,
        detalleVariante: detalle || null,
      })
      .then(
        () => this.resultadoAgregar.set({ tipo: 'agregado', nombre: producto.nombre }),
        // El rechazo se atrapa aquí y se dice. Antes el `void` lo dejaba como una promesa
        // rechazada sin dueño: la persona pulsaba, no pasaba nada y nadie le decía por qué.
        () => this.resultadoAgregar.set({ tipo: 'error' }),
      );
  }
}
