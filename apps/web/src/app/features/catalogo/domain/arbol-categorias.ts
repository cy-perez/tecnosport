import { Categoria } from './producto.model';
import { LINEAS, Linea } from './filtro-productos.model';

/**
 * Una categoría con lo que cuelga de ella. `hijas` vacío es una hoja, y una hoja es lo único a lo
 * que se le puede colgar un producto — lo defiende el backend (`CategoriaNoEsHojaException`).
 */
export interface NodoCategoria {
  readonly categoria: Categoria;
  readonly hijas: readonly NodoCategoria[];
}

/** Una línea con sus categorías de primer nivel colgadas. */
export interface RamaDeLinea {
  readonly linea: Linea;
  readonly nodos: readonly NodoCategoria[];
}

/**
 * La lista plana que devuelve la API, colgada.
 *
 * La API manda `padreId` en cada categoría y nada más: el árbol se arma aquí, en el dominio del
 * frontend, y no en el backend. El motivo es que hay dos consumidores con formas distintas —el
 * menú quiere el árbol, el desplegable del filtro quiere la lista sangrada— y anidar en el
 * contrato obligaría al segundo a deshacer el trabajo del primero.
 *
 * **Tolera un padre que no está en la lista.** No es defensa paranoica: pasa de verdad cuando
 * alguien filtra la lista antes de colgarla, y el resultado de ignorarlo sería una categoría que
 * desaparece del menú sin que nadie sepa por qué. Aquí sube a la raíz, que es visible y por tanto
 * corregible.
 */
export function construirArbolDeCategorias(
  categorias: readonly Categoria[],
): readonly NodoCategoria[] {
  const hijasPorPadre = new Map<string, Categoria[]>();
  const ids = new Set(categorias.map((categoria) => categoria.id));
  const raices: Categoria[] = [];

  for (const categoria of categorias) {
    const padreId = categoria.padreId;
    if (padreId === null || !ids.has(padreId)) {
      raices.push(categoria);
      continue;
    }
    const hermanas = hijasPorPadre.get(padreId);
    if (hermanas) {
      hermanas.push(categoria);
    } else {
      hijasPorPadre.set(padreId, [categoria]);
    }
  }

  const colgar = (categoria: Categoria): NodoCategoria => ({
    categoria,
    hijas: (hijasPorPadre.get(categoria.id) ?? []).map(colgar),
  });

  return raices.map(colgar);
}

/**
 * El árbol repartido por línea, en el orden de `LINEAS` — que es el orden en que el menú pinta las
 * ramas, no el alfabético.
 *
 * **Una línea sin categorías sale igual, con la lista vacía.** Es la misma decisión que hizo a
 * `ListarCategorias` dejar de esconder las categorías vacías: el menú es una promesa de surtido, y
 * una rama que desaparece porque hoy no tiene nada dice que el negocio no vende eso. Quien pinta
 * decide si la muestra apagada o no; lo que no puede es no enterarse.
 */
export function agruparPorLinea(categorias: readonly Categoria[]): readonly RamaDeLinea[] {
  const arbol = construirArbolDeCategorias(categorias);
  return LINEAS.map((linea) => ({
    linea,
    nodos: arbol.filter((nodo) => nodo.categoria.linea === linea),
  }));
}

/**
 * Solo las hojas, con la ruta completa como etiqueta ("Ropa › Dama › Camisas").
 *
 * Es lo que necesita el desplegable de crear y editar producto: un producto cuelga de una hoja, y
 * ofrecer "Dama" ahí sería ofrecer algo que el backend va a rechazar. La ruta va en la etiqueta
 * porque "Busos" aparece dos veces y "Dama" tres — sin ella, el desplegable tiene entradas que no
 * se distinguen.
 */
export function hojasConRuta(
  categorias: readonly Categoria[],
  nombreDeLinea: (linea: string) => string,
): readonly { readonly categoria: Categoria; readonly ruta: string }[] {
  const hojas: { categoria: Categoria; ruta: string }[] = [];

  const recorrer = (nodo: NodoCategoria, ancestros: readonly string[]): void => {
    const ruta = [...ancestros, nodo.categoria.nombre];
    if (nodo.hijas.length === 0) {
      hojas.push({ categoria: nodo.categoria, ruta: ruta.join(SEPARADOR_DE_RUTA) });
      return;
    }
    for (const hija of nodo.hijas) {
      recorrer(hija, ruta);
    }
  };

  for (const nodo of construirArbolDeCategorias(categorias)) {
    recorrer(nodo, [nombreDeLinea(nodo.categoria.linea)]);
  }

  return hojas;
}

/**
 * El separador de la ruta. Es un carácter y no una clave de Transloco porque no es una palabra: se
 * lee igual en español y en inglés, como la barra de una miga de pan.
 */
const SEPARADOR_DE_RUTA = ' › ';
