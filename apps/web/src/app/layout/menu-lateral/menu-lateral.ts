import {
  ChangeDetectionStrategy,
  Component,
  DOCUMENT,
  computed,
  inject,
  signal,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { IsActiveMatchOptions, RouterLink, RouterLinkActive } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { SesionStore } from '../../core/autenticacion/sesion.store';
import {
  agruparPorLinea,
  NodoCategoria,
  RamaDeLinea,
} from '../../features/catalogo/domain/arbol-categorias';
import { Categoria } from '../../features/catalogo/domain/producto.model';
import { REPOSITORIO_CATEGORIAS } from '../../features/catalogo/domain/repositorio-categorias.puerto';
import { iconoCatalogo, iconoChevron, iconoFijar, iconoPanel } from '../../shared/ui/icono/iconos';
import { TsIcono } from '../../shared/ui/icono/ts-icono';
import { MenuLateralStore } from './menu-lateral.store';

/** Un destino del panel administrativo, con su segmento bajo `/{idioma}/admin`. */
interface SeccionDelPanel {
  readonly clave: string;
  readonly segmentos: readonly string[];
}

const SECCIONES_DEL_PANEL: readonly SeccionDelPanel[] = [
  { clave: 'encabezado.menu_panel.pedidos', segmentos: ['pedidos'] },
  { clave: 'encabezado.menu_panel.productos', segmentos: ['productos'] },
  { clave: 'encabezado.menu_panel.categorias', segmentos: ['categorias'] },
  { clave: 'encabezado.menu_panel.marcas', segmentos: ['marcas'] },
  { clave: 'encabezado.menu_panel.atencion', segmentos: ['atencion'] },
  { clave: 'encabezado.menu_panel.envios', segmentos: ['envios'] },
];

/**
 * El menú lateral del sitio: un riel de iconos que se despliega al acercar el puntero.
 *
 * <b>Qué se copió del sitio de referencia y qué no.</b> Se copió el gesto —recogido enseña iconos,
 * al entrar el puntero se ensancha con una transición de 300 ms y al salir vuelve— y el acordeón
 * de cada rama. <b>No</b> se copió que la página entera se desplace: allí el contenido se corre
 * 220 px cada vez que el puntero roza el borde izquierdo, y en una vitrina eso mueve las tarjetas
 * de producto bajo el cursor de quien iba a hacer clic. Aquí el riel reserva sus 72 px en la
 * rejilla y el panel desplegado se pinta <b>encima</b> del contenido, con sombra. El gesto se
 * siente igual y nada salta.
 *
 * <b>Se abre también con el foco del teclado, no solo con el puntero.</b> Un desplegable que solo
 * responde al ratón no lo puede usar quien navega con Tab, y ese es justamente el requisito de la
 * regla de accesibilidad del proyecto. Por eso el estado es una señal y no un `:hover` de CSS:
 * `focusin` la enciende igual que `mouseenter`.
 *
 * <b>Solo desde el primer punto de quiebre.</b> En un teléfono no hay puntero que acercar, así que
 * el árbol se queda donde ya estaba: el panel desplegable del encabezado, que se abre con un
 * botón. Repetirlo aquí sería un menú que nadie puede abrir.
 */
@Component({
  selector: 'app-menu-lateral',
  imports: [NgTemplateOutlet, RouterLink, RouterLinkActive, TranslocoPipe, TsIcono],
  templateUrl: './menu-lateral.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    // `hidden` bajo el punto de quiebre, y no un `@if` con el ancho de la ventana: el ancho no se
    // conoce en el servidor, así que un `@if` haría que el HTML servido y el hidratado no
    // coincidan. Una media query la resuelve el navegador sin que nadie le pregunte nada.
    //
    // `contents` y no `block`, y esto costó entenderlo: `app-root` es una rejilla de **tres**
    // filas —encabezado, contenido y pie—, así que un cuarto hijo en el flujo se lleva una cuarta
    // fila implícita y descuadra el pie. Con `display: contents` el host no genera caja: el único
    // hijo que queda es el `<nav>`, que es `fixed` y por tanto está fuera del flujo. El hueco de
    // 72 px lo reserva el `ps-menu-riel` de `app-root`, no este elemento.
    class: 'hidden desde-movil:contents',
    '(keydown.escape)': 'cerrar()',
  },
})
export class MenuLateral {
  private readonly documento = inject(DOCUMENT);
  private readonly transloco = inject(TranslocoService);
  private readonly repositorio = inject(REPOSITORIO_CATEGORIAS);
  protected readonly sesion = inject(SesionStore);

  protected readonly idiomaActual = this.transloco.activeLang;

  protected readonly iconoCatalogo = iconoCatalogo;
  protected readonly iconoPanel = iconoPanel;
  protected readonly iconoChevron = iconoChevron;
  protected readonly iconoFijar = iconoFijar;

  /**
   * El estado vive en un store de raíz y no aquí porque lo miran además el encabezado —que corre el
   * logo— y `app-root`, que reserva el hueco. Lo explica {@link MenuLateralStore}.
   */
  protected readonly menu = inject(MenuLateralStore);

  protected readonly seccionesDelPanel = SECCIONES_DEL_PANEL;

  /**
   * Marcar el enlace activo exige comparar **también** los query params, y por eso no vale el
   * `routerLinkActive` de siempre: las veinte hojas del árbol apuntan a `/productos` y solo se
   * distinguen por `?categoria=`, así que con la comparación por omisión saldrían las veinte
   * marcadas en cuanto alguien abriera la rejilla.
   */
  protected readonly coincidenciaExacta: IsActiveMatchOptions = {
    paths: 'exact',
    queryParams: 'exact',
    matrixParams: 'ignored',
    fragment: 'ignored',
  };

  /**
   * Llave propia (`['catalogo','menu']`) y no la del filtro de la vitrina, aunque el dato sea el
   * mismo: el menú vive en `layout` y está en todas las pantallas, incluidas las que no proveen el
   * adaptador del panel. Compartir la llave dejaría el contenido del menú a merced de quién la
   * haya pedido primero — el mismo defecto que `usarMarcasAdmin` documenta.
   *
   * `staleTime` largo: el árbol de categorías cambia cuando alguien lo edita en el panel, y ese
   * camino ya invalida esta llave explícitamente (`escribir-categoria.mutacion.ts`). Pedirlo cada
   * pocos segundos en todas las pantallas sería gastar una petición por navegación para un dato
   * que no se mueve solo.
   */
  private readonly consulta = injectQuery(() => ({
    queryKey: ['catalogo', 'menu'],
    queryFn: () => this.repositorio.listarTodas(),
    staleTime: 5 * 60 * 1000,
  }));

  protected readonly ramas = computed<readonly RamaDeLinea[]>(() =>
    agruparPorLinea((this.consulta.data() ?? []) as readonly Categoria[]),
  );

  /** ¿El panel está abierto? Lo enciende el puntero, el foco, o el botón de fijar. */
  protected readonly desplegado = this.menu.abierto;

  /** Los grupos abiertos, por clave. "Catálogo" arranca abierto: es para lo que existe el menú. */
  private readonly abiertos = signal<ReadonlySet<string>>(new Set(['catalogo']));

  protected estaAbierto(clave: string): boolean {
    return this.abiertos().has(clave);
  }

  protected alternar(clave: string): void {
    const siguiente = new Set(this.abiertos());
    if (!siguiente.delete(clave)) {
      siguiente.add(clave);
    }
    this.abiertos.set(siguiente);
  }

  protected abrir(): void {
    this.menu.abrirPorElGesto();
  }

  protected alternarFijado(): void {
    this.menu.alternarFijado();
  }

  /**
   * Se recoge al salir el puntero o el foco. **Con el panel fijado no recoge nada**: eso es lo que
   * significa fijarlo.
   *
   * Los grupos abiertos **no** se cierran: quien vuelve a acercar el puntero espera encontrar la
   * rama donde la dejó, no el menú reiniciado. Es estado de navegación, no del gesto.
   */
  protected cerrar(): void {
    this.menu.cerrarPorElGesto();
  }

  /**
   * `focusout` dispara también al pasar el foco de un hijo a otro dentro del propio menú, y
   * entonces `relatedTarget` sigue estando dentro: recoger ahí cerraría el panel en mitad de un
   * recorrido con Tab. Solo se recoge cuando el foco se fue de verdad.
   */
  protected alSalirElFoco(evento: FocusEvent, panel: HTMLElement): void {
    const destino = evento.relatedTarget;
    if (destino instanceof Node && panel.contains(destino)) {
      return;
    }
    this.cerrar();
  }

  protected readonly sinMovimiento = this.menu.sinMovimiento;

  /** Una rama del árbol tiene hijas: entonces se pliega en vez de enlazar directamente. */
  protected tieneHijas(nodo: NodoCategoria): boolean {
    return nodo.hijas.length > 0;
  }
}
