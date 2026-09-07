import { InjectionToken } from '@angular/core';

export interface SetRotacionAdmin {
  readonly id: string;
  readonly productoId: string;
  readonly fotogramasPrometidos: number;
  readonly estado: 'BORRADOR' | 'COMPLETO' | 'PUBLICADO';
  readonly imagenes: readonly { readonly orden: number; readonly urlWebp: string }[];
}

export interface SubidaDeFotograma {
  readonly orden: number;
  readonly url: string;
  readonly objectKey: string;
}

export interface AbrirSetRotacion {
  readonly productoId: string;
  readonly fotogramas: number;
  readonly dispositivo: string;
  readonly versionAsistente: string;
}

export interface FotogramaSubido {
  readonly orden: number;
  readonly objectKey: string;
  readonly ancho: number;
  readonly alto: number;
  /** El SHA-256 del fotograma que se subió, en hexadecimal (docs/02-modelo-datos.md). */
  readonly hash: string;
}

/** Los cinco pasos del set en el backend (docs/03-api.md, ADR-0018). */
export interface RepositorioSetsRotacion {
  abrir(comando: AbrirSetRotacion): Promise<SetRotacionAdmin>;

  /** Una URL firmada por fotograma. Cuántas son lo decide el set, no el cliente. */
  urlsDeSubida(setId: string, contentType: string): Promise<SubidaDeFotograma[]>;

  /** El `PUT` directo a Cloud Storage: los bytes no pasan por el backend. */
  subirFotograma(url: string, imagen: Blob): Promise<void>;

  completar(setId: string, fotogramas: readonly FotogramaSubido[]): Promise<SetRotacionAdmin>;

  publicar(setId: string): Promise<SetRotacionAdmin>;

  eliminar(setId: string): Promise<void>;
}

export const REPOSITORIO_SETS_ROTACION = new InjectionToken<RepositorioSetsRotacion>(
  'RepositorioSetsRotacion',
);
