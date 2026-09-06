import { InjectionToken } from '@angular/core';

/** Un fotograma recién tomado, sin procesar: los píxeles tal como salieron de la cámara. */
export interface FotogramaCrudo {
  /** URL de objeto: hay que liberarla con `Camara.liberar` cuando deje de usarse. */
  readonly url: string;
  readonly ancho: number;
  readonly alto: number;
}

/**
 * La cámara del dispositivo. Es un puerto y no `navigator.mediaDevices` a pelo por tres motivos:
 * no existe en el servidor, la pantalla tiene que poder probarse sin cámara, y el camino
 * degradado solo se construye de verdad si alguien lo puede simular.
 */
export interface Camara {
  /** Falso en el servidor y en un navegador sin `getUserMedia` (contexto inseguro, por ejemplo). */
  disponible(): boolean;

  /** Abre la cámara trasera. Rechaza si el permiso se niega. Debe llamarse desde un gesto. */
  abrir(): Promise<MediaStream>;

  cerrar(stream: MediaStream): void;

  /** Congela el fotograma actual del video a la resolución real del sensor. */
  capturar(video: HTMLVideoElement): Promise<FotogramaCrudo>;

  liberar(fotograma: FotogramaCrudo): void;
}

export const CAMARA = new InjectionToken<Camara>('Camara');
