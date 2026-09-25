import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { filter, map } from 'rxjs';
import { cn } from '../../../shared/ui/cn';

/** Una pestaña: su etiqueta y los segmentos que la identifican **dentro** de `/:lang/admin/`. */
interface PestanaAdmin {
  readonly clave: string;
  readonly segmentos: readonly string[];
}

/**
 * Las secciones del panel, en el orden en que se recorren.
 *
 * <p>`Resumen` primero porque es la que sirve `/:lang/admin`, y `Cambiar mi clave` al final porque
 * es lo único de la lista que no es trabajo del negocio. Entre medias van juntas las que se
 * consultan seguidas: pedidos, el catálogo con sus dos listas de inventario, y las dos bandejas.
 */
const PESTANAS: readonly PestanaAdmin[] = [
  { clave: 'admin.panel.ir_a_panel', segmentos: ['panel'] },
  { clave: 'admin.panel.ir_a_pedidos', segmentos: ['pedidos'] },
  { clave: 'admin.panel.ir_a_productos', segmentos: ['productos'] },
  { clave: 'admin.panel.ir_a_existencias', segmentos: ['productos', 'existencias'] },
  { clave: 'admin.panel.ir_a_medidas', segmentos: ['productos', 'medidas'] },
  { clave: 'admin.panel.ir_a_marcas', segmentos: ['marcas'] },
  { clave: 'admin.panel.ir_a_categorias', segmentos: ['categorias'] },
  { clave: 'admin.panel.ir_a_atencion', segmentos: ['atencion'] },
  { clave: 'admin.panel.ir_a_envios', segmentos: ['envios'] },
  { clave: 'admin.panel.ir_a_clave', segmentos: ['clave'] },
];

/**
 * Lo que comparten las diez, y lo que las separa. Compuesto en el componente y no repartido entre
 * un `class` estático y un `[class]` en la plantilla: los dos conviven —Angular los fusiona— pero
 * leyendo la plantilla no se ve de dónde sale la lista final, que es justo lo que `cn` existe para
 * evitar.
 *
 * `min-h-tactil` también aquí: una pestaña se pulsa con el pulgar igual que un botón, y en esta
 * barra hay diez seguidas.
 */
const CLASES_PESTANA =
  'anillo-foco inline-flex min-h-tactil items-center rounded-md px-16 text-sm font-medio ' +
  'whitespace-nowrap no-underline';

/**
 * La activa se eleva sobre el riel: fondo de superficie, sombra corta y el texto en el color
 * pleno. Las demás van en `texto-suave` y solo suben a `texto` con el ratón, que es la misma
 * afordancia que usan las baldosas de la portada.
 *
 * <p>El `oscuro:` no sobra: cuál de las dos superficies "sube" se invierte con el tema —en claro
 * brilla `superficie`, en oscuro `superficie-alt`— así que con un solo par la pestaña activa
 * quedaría más oscura que el riel justo en tema oscuro. Es la misma corrección que
 * `ts-alternador-idioma` ya lleva, y de las que `docs/04-ui-marca.md` avisa que no atrapa ninguna
 * prueba: se ve en el navegador o no se ve.
 */
const CLASES_ACTIVA = 'bg-ts-superficie text-ts-texto shadow-sm oscuro:bg-ts-superficie-alt';
const CLASES_INACTIVA = 'text-ts-texto-suave hover:text-ts-texto';

/**
 * Qué pestaña está activa para una URL, **la más específica que encaje**.
 *
 * <p>Existe como función pura y no como un `routerLinkActive` por enlace porque `routerLinkActive`
 * no sabe resolver el empate que esta lista tiene de verdad: `/admin/productos/existencias` encaja
 * con `Productos` y con `Existencias` a la vez. Con `exact: false` se encienden las dos; con
 * `exact: true`, `Productos` se apaga en `/admin/productos/crear` y en `/admin/productos/{id}/
 * editar`, que son sitios donde quien mira sigue estando en productos. Ganando la coincidencia más
 * larga, las dos cosas salen bien.
 *
 * @param url la URL del router, con su prefijo de idioma y su `?query` si lo trae.
 * @returns la clave de la pestaña activa, o `null` si la URL no es del panel.
 */
export function pestanaActiva(url: string): string | null {
  const camino = url.split('?')[0].split('#')[0];
  const partes = camino.split('/').filter((parte) => parte.length > 0);
  const indice = partes.indexOf('admin');
  if (indice === -1) {
    return null;
  }
  const dentro = partes.slice(indice + 1);

  let ganadora: PestanaAdmin | null = null;
  for (const pestana of PESTANAS) {
    const encaja = pestana.segmentos.every((segmento, i) => dentro[i] === segmento);
    if (encaja && (ganadora === null || pestana.segmentos.length > ganadora.segmentos.length)) {
      ganadora = pestana;
    }
  }
  return ganadora?.clave ?? null;
}

/**
 * La barra de secciones del panel, al estilo del control segmentado de TailAdmin.
 *
 * <p><b>Es navegación, no un `tablist`.</b> Se ve como pestañas y se usa como pestañas, pero cada
 * una lleva a una ruta distinta con su propia URL: `role="tablist"` le prometería a un lector de
 * pantalla paneles que se intercambian en el sitio, flechas para moverse entre ellos y un
 * `tabpanel` que aquí no existe. Un `<nav>` con enlaces y `aria-current="page"` dice la verdad, y
 * el aspecto es el mismo.
 *
 * <p>Vive en el marco y no en la pantalla del panel: unas pestañas que desaparecen al pulsar una
 * son un menú, no pestañas. Por eso `marco-admin.page.ts` envuelve a todas las pantallas con
 * sesión — `iniciar-sesion` queda fuera, que es donde todavía no hay a dónde ir.
 *
 * <p>La barra se desplaza sola en horizontal (`overflow-x-auto` con la lista en `w-max`) en vez de
 * partirse en dos filas: diez pestañas no caben en un teléfono de ninguna forma, y una barra de dos
 * alturas deja de leerse como una barra.
 */
@Component({
  selector: 'app-pestanas-admin',
  imports: [RouterLink, TranslocoPipe],
  templateUrl: './pestanas-admin.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PestanasAdmin {
  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);

  protected readonly pestanas = PESTANAS;

  /**
   * La URL actual. `router.url` a secas no es una señal y no se reevalúa al navegar; el evento sí.
   * El valor inicial viene del propio router para que el primer render —y el del servidor— ya
   * marque la pestaña correcta, sin esperar a un `NavigationEnd` que en SSR no llega.
   */
  private readonly url = toSignal(
    this.router.events.pipe(
      filter((evento): evento is NavigationEnd => evento instanceof NavigationEnd),
      map((evento) => evento.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  private readonly activa = computed(() => pestanaActiva(this.url()));

  protected esActiva(pestana: PestanaAdmin): boolean {
    return this.activa() === pestana.clave;
  }

  protected clasesDe(pestana: PestanaAdmin): string {
    return cn(CLASES_PESTANA, this.esActiva(pestana) ? CLASES_ACTIVA : CLASES_INACTIVA);
  }

  /** El destino absoluto, con el prefijo de idioma: las rutas del sitio lo llevan siempre. */
  protected destino(pestana: PestanaAdmin): unknown[] {
    return ['/', this.transloco.activeLang(), 'admin', ...pestana.segmentos];
  }
}
