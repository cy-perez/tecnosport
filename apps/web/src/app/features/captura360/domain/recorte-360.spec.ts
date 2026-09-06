import {
  colorDeFondo,
  detectarRectanguloDelProducto,
  encuadreDelSet,
  luminancia,
  recorteDeFotograma,
  type ColorRgb,
  type DatosImagen,
  type Rectangulo,
} from './recorte-360';

/** Fondo de estudio: claro y uniforme, la condición que pide `docs/10-captura-360.md`. */
const FONDO_CLARO: ColorRgb = { r: 240, g: 240, b: 240 };
/** Un producto oscuro, que es el caso cómodo de la binarización. */
const PRODUCTO_OSCURO: ColorRgb = { r: 40, g: 40, b: 40 };

interface Figura {
  readonly rectangulo: Rectangulo;
  readonly color: ColorRgb;
}

/** Imagen sintética: un fondo plano y las figuras que pida la prueba, pintadas encima. */
function imagenDePrueba(opciones: {
  ancho: number;
  alto: number;
  fondo: ColorRgb;
  figuras?: readonly Figura[];
}): DatosImagen {
  const { ancho, alto, fondo } = opciones;
  const datos = new Uint8ClampedArray(ancho * alto * 4);

  for (let i = 0; i < ancho * alto; i++) {
    datos[i * 4] = fondo.r;
    datos[i * 4 + 1] = fondo.g;
    datos[i * 4 + 2] = fondo.b;
    datos[i * 4 + 3] = 255;
  }

  for (const figura of opciones.figuras ?? []) {
    const { x, y, ancho: anchoFigura, alto: altoFigura } = figura.rectangulo;
    for (let fila = y; fila < y + altoFigura; fila++) {
      for (let columna = x; columna < x + anchoFigura; columna++) {
        const i = (fila * ancho + columna) * 4;
        datos[i] = figura.color.r;
        datos[i + 1] = figura.color.g;
        datos[i + 2] = figura.color.b;
        datos[i + 3] = 255;
      }
    }
  }

  return { ancho, alto, datos };
}

describe('luminancia', () => {
  it('va de 0 en el negro a 255 en el blanco', () => {
    expect(luminancia(0, 0, 0)).toBe(0);
    expect(luminancia(255, 255, 255)).toBeCloseTo(255);
  });

  it('el verde pesa más que el rojo, y el rojo más que el azul', () => {
    expect(luminancia(0, 255, 0)).toBeGreaterThan(luminancia(255, 0, 0));
    expect(luminancia(255, 0, 0)).toBeGreaterThan(luminancia(0, 0, 255));
  });
});

describe('colorDeFondo', () => {
  it('devuelve el color del fondo de un estudio bien montado', () => {
    const fondo = colorDeFondo(imagenDePrueba({ ancho: 200, alto: 150, fondo: FONDO_CLARO }));

    expect(fondo).not.toBeNull();
    expect(fondo?.r).toBeCloseTo(240);
    expect(fondo?.g).toBeCloseTo(240);
    expect(fondo?.b).toBeCloseTo(240);
  });

  it('el producto en el centro no contamina la estimación', () => {
    const conProducto = imagenDePrueba({
      ancho: 200,
      alto: 150,
      fondo: FONDO_CLARO,
      figuras: [{ rectangulo: { x: 80, y: 55, ancho: 40, alto: 40 }, color: PRODUCTO_OSCURO }],
    });

    expect(colorDeFondo(conProducto)?.r).toBeCloseTo(240);
  });

  it('una esquina en sombra significa que no hay fondo del que fiarse', () => {
    const conSombra = imagenDePrueba({
      ancho: 200,
      alto: 150,
      fondo: FONDO_CLARO,
      figuras: [
        { rectangulo: { x: 0, y: 0, ancho: 30, alto: 30 }, color: { r: 150, g: 150, b: 150 } },
      ],
    });

    expect(colorDeFondo(conSombra)).toBeNull();
  });

  it('sin píxeles suficientes no hay nada que estimar', () => {
    const truncada: DatosImagen = { ancho: 200, alto: 150, datos: new Uint8ClampedArray(100) };

    expect(colorDeFondo(truncada)).toBeNull();
  });
});

describe('detectarRectanguloDelProducto', () => {
  const conProductoEn = (rectangulo: Rectangulo): DatosImagen =>
    imagenDePrueba({
      ancho: 200,
      alto: 150,
      fondo: FONDO_CLARO,
      figuras: [{ rectangulo, color: PRODUCTO_OSCURO }],
    });

  it('devuelve la envolvente exacta del producto', () => {
    const deteccion = detectarRectanguloDelProducto(
      conProductoEn({ x: 40, y: 25, ancho: 20, alto: 30 }),
    );

    expect(deteccion.ok).toBe(true);
    expect(deteccion.ok && deteccion.rectangulo).toEqual({ x: 40, y: 25, ancho: 20, alto: 30 });
  });

  it('devuelve también el fondo estimado, que es con lo que se rellena el cuadro de salida', () => {
    const deteccion = detectarRectanguloDelProducto(
      conProductoEn({ x: 40, y: 25, ancho: 20, alto: 30 }),
    );

    expect(deteccion.ok && deteccion.fondo.r).toBeCloseTo(240);
  });

  it('un píxel suelto de polvo no estira el rectángulo hasta el otro extremo', () => {
    const conPolvo = imagenDePrueba({
      ancho: 200,
      alto: 150,
      fondo: FONDO_CLARO,
      figuras: [
        { rectangulo: { x: 40, y: 25, ancho: 20, alto: 30 }, color: PRODUCTO_OSCURO },
        { rectangulo: { x: 180, y: 140, ancho: 1, alto: 1 }, color: PRODUCTO_OSCURO },
      ],
    });

    const deteccion = detectarRectanguloDelProducto(conPolvo);

    expect(deteccion.ok && deteccion.rectangulo).toEqual({ x: 40, y: 25, ancho: 20, alto: 30 });
  });

  it('un fondo vacío no tiene producto que recortar', () => {
    const deteccion = detectarRectanguloDelProducto(
      imagenDePrueba({ ancho: 200, alto: 150, fondo: FONDO_CLARO }),
    );

    expect(deteccion).toEqual({ ok: false, motivo: 'SIN_PRODUCTO' });
  });

  it('un producto que toca el marco se rechaza: no hay dónde poner el margen del set', () => {
    const deteccion = detectarRectanguloDelProducto(
      conProductoEn({ x: 40, y: 25, ancho: 160, alto: 30 }),
    );

    expect(deteccion).toEqual({ ok: false, motivo: 'PRODUCTO_CORTADO' });
  });

  it('con las esquinas desiguales no se inventa un recorte', () => {
    const conSombra = imagenDePrueba({
      ancho: 200,
      alto: 150,
      fondo: FONDO_CLARO,
      figuras: [
        { rectangulo: { x: 0, y: 0, ancho: 30, alto: 30 }, color: { r: 150, g: 150, b: 150 } },
        { rectangulo: { x: 80, y: 55, ancho: 40, alto: 40 }, color: PRODUCTO_OSCURO },
      ],
    });

    expect(detectarRectanguloDelProducto(conSombra)).toEqual({
      ok: false,
      motivo: 'FONDO_NO_UNIFORME',
    });
  });

  it('unos datos que no describen una imagen se dicen, no se procesan', () => {
    const truncada: DatosImagen = { ancho: 200, alto: 150, datos: new Uint8ClampedArray(100) };

    expect(detectarRectanguloDelProducto(truncada)).toEqual({
      ok: false,
      motivo: 'IMAGEN_INVALIDA',
    });
  });

  it('el límite honesto: un producto de la misma luminancia que el fondo no se ve', () => {
    // Un ámbar claro (255, 240, 200) tiene casi la misma luminancia que el gris (240, 240, 240):
    // se binariza por luminancia, como dice el documento, así que este caso pide recorte manual.
    const camuflado = imagenDePrueba({
      ancho: 200,
      alto: 150,
      fondo: FONDO_CLARO,
      figuras: [
        { rectangulo: { x: 80, y: 55, ancho: 40, alto: 40 }, color: { r: 255, g: 240, b: 200 } },
      ],
    });

    expect(detectarRectanguloDelProducto(camuflado)).toEqual({ ok: false, motivo: 'SIN_PRODUCTO' });
  });
});

describe('encuadreDelSet', () => {
  it('sin margen, el lado del recorte es el lado mayor del set', () => {
    const encuadre = encuadreDelSet(
      [
        { x: 0, y: 0, ancho: 100, alto: 80 },
        { x: 0, y: 0, ancho: 140, alto: 90 },
      ],
      { margenRelativo: 0, ladoSalidaPx: 1000 },
    );

    expect(encuadre?.ladoFuentePx).toBe(140);
  });

  it('manda el lado mayor aunque sea el alto de otro fotograma, no el ancho del más ancho', () => {
    // El tenis de perfil es más alto que ancho el de frente. Escalar por "el más ancho" lo
    // cortaría por arriba y por abajo.
    const encuadre = encuadreDelSet(
      [
        { x: 0, y: 0, ancho: 140, alto: 90 },
        { x: 0, y: 0, ancho: 100, alto: 180 },
      ],
      { margenRelativo: 0, ladoSalidaPx: 1000 },
    );

    expect(encuadre?.ladoFuentePx).toBe(180);
  });

  it('el margen se aplica a los dos lados del cuadrado', () => {
    const encuadre = encuadreDelSet([{ x: 0, y: 0, ancho: 100, alto: 100 }], {
      margenRelativo: 0.1,
      ladoSalidaPx: 1000,
    });

    expect(encuadre?.ladoFuentePx).toBeCloseTo(120);
  });

  it('la escala lleva el cuadrado recortado al lado de salida', () => {
    const encuadre = encuadreDelSet([{ x: 0, y: 0, ancho: 500, alto: 400 }], {
      margenRelativo: 0,
      ladoSalidaPx: 1000,
    });

    expect(encuadre?.escala).toBeCloseTo(2);
  });

  it('la salida es de 1000 px por omisión', () => {
    const encuadre = encuadreDelSet([{ x: 0, y: 0, ancho: 500, alto: 400 }], { margenRelativo: 0 });

    expect(encuadre?.escala).toBeCloseTo(2);
  });

  it('el margen por omisión es el 8% del lado mayor, a cada lado', () => {
    const encuadre = encuadreDelSet([{ x: 0, y: 0, ancho: 100, alto: 100 }]);

    expect(encuadre?.ladoFuentePx).toBeCloseTo(116);
  });

  it('un set vacío no tiene encuadre común', () => {
    expect(encuadreDelSet([], { margenRelativo: 0.1 })).toBeNull();
  });

  it('un rectángulo degenerado invalida el set entero, en vez de adivinar una escala', () => {
    const conFotogramaSinDetectar = [
      { x: 0, y: 0, ancho: 100, alto: 80 },
      { x: 0, y: 0, ancho: 0, alto: 0 },
    ];

    expect(encuadreDelSet(conFotogramaSinDetectar, { margenRelativo: 0.1 })).toBeNull();
  });

  it('un margen negativo no encoge el recorte por debajo del producto', () => {
    expect(
      encuadreDelSet([{ x: 0, y: 0, ancho: 100, alto: 80 }], { margenRelativo: -0.5 }),
    ).toBeNull();
  });
});

describe('recorteDeFotograma', () => {
  const imagen = { ancho: 1200, alto: 900 };

  it('centra el cuadrado en el producto y llena todo el cuadro de salida', () => {
    const encuadre = { ladoFuentePx: 400, escala: 2.5 };
    const producto = { x: 500, y: 350, ancho: 200, alto: 200 };

    const recorte = recorteDeFotograma(producto, encuadre, imagen);

    // El centro del producto es (600, 450); el cuadrado de 400 arranca 200 antes.
    expect(recorte.origen).toEqual({ x: 400, y: 250, ancho: 400, alto: 400 });
    expect(recorte.destino).toEqual({ x: 0, y: 0, ancho: 1000, alto: 1000 });
  });

  it('el producto queda centrado aunque esté descentrado en la toma', () => {
    const encuadre = { ladoFuentePx: 400, escala: 2.5 };
    const producto = { x: 800, y: 600, ancho: 100, alto: 60 };

    const recorte = recorteDeFotograma(producto, encuadre, imagen);

    const centroDelOrigen = { x: recorte.origen.x + 200, y: recorte.origen.y + 200 };
    expect(centroDelOrigen).toEqual({ x: 850, y: 630 });
  });

  it('dos fotogramas con el producto de distinto tamaño recortan el mismo cuadrado', () => {
    const encuadre = { ladoFuentePx: 400, escala: 2.5 };

    const deFrente = recorteDeFotograma(
      { x: 500, y: 350, ancho: 200, alto: 200 },
      encuadre,
      imagen,
    );
    const dePerfil = recorteDeFotograma(
      { x: 550, y: 400, ancho: 100, alto: 180 },
      encuadre,
      imagen,
    );

    // Misma ventana y misma escala: en la salida, el de perfil se ve más angosto de verdad, no
    // reencuadrado para llenar el cuadro. Es la diferencia entre una rotación y un carrusel.
    expect(dePerfil.origen.ancho).toBe(deFrente.origen.ancho);
    expect(dePerfil.destino).toEqual(deFrente.destino);
  });

  it('cerca de un borde recorta el origen y corre el destino en la misma proporción', () => {
    const encuadre = { ladoFuentePx: 400, escala: 2.5 };
    // El centro está a 100 px del borde izquierdo: 100 px del cuadrado se salen de la toma.
    const producto = { x: 60, y: 350, ancho: 80, alto: 200 };

    const recorte = recorteDeFotograma(producto, encuadre, imagen);

    expect(recorte.origen).toEqual({ x: 0, y: 250, ancho: 300, alto: 400 });
    expect(recorte.destino).toEqual({ x: 250, y: 0, ancho: 750, alto: 1000 });
    expect(recorte.destino.x + recorte.destino.ancho).toBe(1000);
  });

  it('un cuadrado que cae entero fuera de la toma no dibuja nada', () => {
    const recorte = recorteDeFotograma(
      { x: 5000, y: 0, ancho: 10, alto: 10 },
      { ladoFuentePx: 40, escala: 25 },
      { ancho: 100, alto: 100 },
    );

    expect(recorte.origen).toEqual({ x: 0, y: 0, ancho: 0, alto: 0 });
    expect(recorte.destino).toEqual({ x: 0, y: 0, ancho: 0, alto: 0 });
  });
});

describe('el set completo', () => {
  it('el producto se ve del mismo tamaño relativo en los tres fotogramas', () => {
    const imagen = { ancho: 1200, alto: 900 };
    const productos: Rectangulo[] = [
      { x: 500, y: 350, ancho: 200, alto: 160 },
      { x: 560, y: 340, ancho: 120, alto: 180 },
      { x: 520, y: 360, ancho: 180, alto: 150 },
    ];

    const encuadre = encuadreDelSet(productos, { margenRelativo: 0.1 })!;
    const anchosEnSalida = productos.map((producto) => producto.ancho * encuadre.escala);

    // Lo que se ve en la salida guarda la proporción real entre fotogramas: el que mide 120 en la
    // toma se ve 120/200 de lo que se ve el que mide 200. Si cada fotograma se escalara por su
    // cuenta, los tres se verían iguales y el producto "latiría" al girar.
    expect(anchosEnSalida[1] / anchosEnSalida[0]).toBeCloseTo(120 / 200);
    expect(anchosEnSalida[2] / anchosEnSalida[0]).toBeCloseTo(180 / 200);

    // Y el fotograma más grande cabe entero, con su margen, dentro de los 1000 px.
    const mayor = recorteDeFotograma(productos[1], encuadre, imagen);
    expect(mayor.destino.ancho).toBeCloseTo(1000);
    expect(180 * encuadre.escala).toBeLessThan(1000);
  });
});
