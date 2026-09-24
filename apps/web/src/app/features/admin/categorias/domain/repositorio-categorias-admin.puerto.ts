import { InjectionToken } from '@angular/core';
import { Categoria } from '../../../catalogo/domain/producto.model';

/** Lo que el panel manda al crear una categoría. Ver `CrearCategoriaComando` en el backend. */
export interface NuevaCategoria {
  readonly nombre: string;
  /** Vacío = derívalo del nombre, con el slug del padre delante si hay padre. */
  readonly slug?: string;
  /** Obligatoria solo si no hay padre: con padre se hereda. */
  readonly linea?: string;
  readonly padreId?: string;
}

/** Renombrar y mover son la misma operación, por lo mismo que en el backend. */
export interface CambioDeCategoria {
  readonly id: string;
  readonly nombre: string;
  /** Vacío = déjalo como está. **No** se vuelve a derivar del nombre: el slug está en URLs. */
  readonly slug?: string;
  readonly linea?: string;
  readonly padreId?: string;
}

/**
 * Lo que el panel puede hacer con el árbol: verlo entero, crear, editar y borrar.
 *
 * Separado de `RepositorioCategorias` (el de la vitrina) por lo mismo que en marcas: aquel lo
 * implementa también el adaptador público, y la tienda nunca debe poder escribir en el catálogo.
 * Un puerto que su implementación pública no puede cumplir se acaba cumpliendo con un método que
 * lanza.
 */
export interface RepositorioCategoriasAdmin {
  listarTodas(): Promise<Categoria[]>;
  crear(nueva: NuevaCategoria): Promise<ResultadoEscritura>;
  editar(cambio: CambioDeCategoria): Promise<ResultadoEscritura>;
  eliminar(id: string): Promise<ResultadoEscritura>;
}

/**
 * Los rechazos del árbol son **respuestas**, no fallos, y por eso viajan como resultado y no como
 * excepción: cada uno tiene una salida concreta que la pantalla tiene que poder nombrar —"mueve
 * primero sus productos", "borra antes sus subcategorías"— y para eso no puede andar leyendo
 * códigos HTTP. Mismo criterio que `ResultadoCrearMarca` y que `SIN_COBERTURA` en el checkout.
 */
export type ResultadoEscritura =
  | { readonly tipo: 'OK'; readonly categoria: Categoria | null }
  | { readonly tipo: 'SLUG_REPETIDO' }
  | { readonly tipo: 'TIENE_PRODUCTOS' }
  | { readonly tipo: 'TIENE_SUBCATEGORIAS' }
  | { readonly tipo: 'DEMASIADO_PROFUNDA' }
  | { readonly tipo: 'CICLO' };

export const REPOSITORIO_CATEGORIAS_ADMIN = new InjectionToken<RepositorioCategoriasAdmin>(
  'RepositorioCategoriasAdmin',
);
