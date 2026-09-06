import { Injectable } from '@angular/core';
import { ProcesadorDeFotogramas } from '../domain/procesador-fotogramas.puerto';
import {
  ColorRgb,
  DeteccionDeRecorte,
  detectarRectanguloDelProducto,
  EncuadreDelSet,
  LADO_SALIDA_PX,
  Rectangulo,
  recorteDeFotograma,
} from '../domain/recorte-360';

/** WebP con calidad 82 (`docs/10-captura-360.md`). */
const CALIDAD_WEBP = 0.82;

/**
 * `<canvas>` de verdad. Ninguna decisión de recorte ni de escala se toma aquí: eso ya está
 * resuelto y probado en `recorte-360.ts`, y este adaptador solo pone y saca píxeles.
 *
 * **Solo WebP, sin respaldo JPEG.** El documento pedía los dos, pero
 * `POST /api/v1/admin/sets-rotacion/{id}/subidas` emite una key por fotograma: el respaldo
 * exigiría 2N objetos y una columna más en el modelo. El visor ya sirve `urlWebp` con `url` de
 * reserva y ambas apuntan al mismo objeto — ver el registro de esta decisión en
 * docs/09-plan-de-arranque.md.
 */
@Injectable()
export class ProcesadorCanvas implements ProcesadorDeFotogramas {
  async medir(toma: Blob): Promise<DeteccionDeRecorte> {
    const imagen = await createImageBitmap(toma);
    try {
      const lienzo = this.lienzo(imagen.width, imagen.height);
      const contexto = this.contexto(lienzo);
      contexto.drawImage(imagen, 0, 0);
      const datos = contexto.getImageData(0, 0, imagen.width, imagen.height);
      return detectarRectanguloDelProducto({
        ancho: datos.width,
        alto: datos.height,
        datos: datos.data,
      });
    } finally {
      // Un bitmap de 1920x1920 son catorce megas en memoria: se sueltan antes del siguiente.
      imagen.close();
    }
  }

  async renderizar(
    toma: Blob,
    rectangulo: Rectangulo,
    encuadre: EncuadreDelSet,
    fondo: ColorRgb,
  ): Promise<Blob> {
    const imagen = await createImageBitmap(toma);
    try {
      const recorte = recorteDeFotograma(rectangulo, encuadre, {
        ancho: imagen.width,
        alto: imagen.height,
      });

      const lienzo = this.lienzo(LADO_SALIDA_PX, LADO_SALIDA_PX);
      const contexto = this.contexto(lienzo);
      // El fondo estimado primero: si el cuadrado se sale de la toma, el hueco queda del color
      // del fondo real y no en transparente.
      contexto.fillStyle = `rgb(${Math.round(fondo.r)} ${Math.round(fondo.g)} ${Math.round(fondo.b)})`;
      contexto.fillRect(0, 0, LADO_SALIDA_PX, LADO_SALIDA_PX);

      if (recorte.origen.ancho > 0 && recorte.origen.alto > 0) {
        contexto.drawImage(
          imagen,
          recorte.origen.x,
          recorte.origen.y,
          recorte.origen.ancho,
          recorte.origen.alto,
          recorte.destino.x,
          recorte.destino.y,
          recorte.destino.ancho,
          recorte.destino.alto,
        );
      }

      return await this.aBlob(lienzo);
    } finally {
      imagen.close();
    }
  }

  private lienzo(ancho: number, alto: number): HTMLCanvasElement {
    const lienzo = document.createElement('canvas');
    lienzo.width = ancho;
    lienzo.height = alto;
    return lienzo;
  }

  private contexto(lienzo: HTMLCanvasElement): CanvasRenderingContext2D {
    // `willReadFrequently` evita que el navegador suba el lienzo a la GPU para tener que bajarlo
    // enseguida: aquí siempre se lee lo que se acaba de dibujar.
    const contexto = lienzo.getContext('2d', { willReadFrequently: true });
    if (contexto === null) {
      throw new Error('El navegador no da un contexto 2d.');
    }
    return contexto;
  }

  private aBlob(lienzo: HTMLCanvasElement): Promise<Blob> {
    return new Promise((resolver, rechazar) => {
      lienzo.toBlob(
        (blob) =>
          blob === null ? rechazar(new Error('El lienzo no produjo imagen.')) : resolver(blob),
        'image/webp',
        CALIDAD_WEBP,
      );
    });
  }
}
