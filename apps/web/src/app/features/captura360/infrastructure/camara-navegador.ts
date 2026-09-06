import { Injectable } from '@angular/core';
import { Camara, FotogramaCrudo } from '../domain/camara.puerto';

/**
 * `getUserMedia` de verdad. Pide la cámara trasera y la mayor resolución que el dispositivo dé:
 * el recorte posterior tira píxeles, así que conviene que sobren.
 */
@Injectable()
export class CamaraNavegador implements Camara {
  disponible(): boolean {
    return (
      typeof navigator !== 'undefined' &&
      typeof navigator.mediaDevices?.getUserMedia === 'function' &&
      typeof HTMLCanvasElement !== 'undefined'
    );
  }

  abrir(): Promise<MediaStream> {
    return navigator.mediaDevices.getUserMedia({
      video: {
        // `ideal` y no `exact`: en un portátil sin cámara trasera, `exact` falla en seco en vez
        // de dar la que hay, y no poder capturar desde el escritorio no ayuda a nadie.
        facingMode: { ideal: 'environment' },
        width: { ideal: 1920 },
        height: { ideal: 1920 },
      },
      audio: false,
    });
  }

  cerrar(stream: MediaStream): void {
    for (const pista of stream.getTracks()) {
      pista.stop();
    }
  }

  async capturar(video: HTMLVideoElement): Promise<FotogramaCrudo> {
    const ancho = video.videoWidth;
    const alto = video.videoHeight;
    if (ancho <= 0 || alto <= 0) {
      throw new Error('La cámara todavía no entrega imagen.');
    }

    const lienzo = document.createElement('canvas');
    lienzo.width = ancho;
    lienzo.height = alto;
    const contexto = lienzo.getContext('2d');
    if (contexto === null) {
      throw new Error('El navegador no da un contexto 2d.');
    }
    contexto.drawImage(video, 0, 0, ancho, alto);

    const blob = await new Promise<Blob | null>((resolver) =>
      lienzo.toBlob(resolver, 'image/webp', 0.92),
    );
    if (blob === null) {
      throw new Error('El navegador no pudo convertir el fotograma.');
    }

    return { url: URL.createObjectURL(blob), ancho, alto };
  }

  liberar(fotograma: FotogramaCrudo): void {
    URL.revokeObjectURL(fotograma.url);
  }
}
