# De dónde sale la ficha técnica

`descripciones.md` dice **cómo** se escribe una ficha. Este documento dice **de
dónde salen los datos**, que es la parte que cuesta: cada afirmación técnica
tiene que venir de la ficha oficial del fabricante, y no todos los fabricantes
la publican en Colombia.

El orden es siempre el mismo: sitio oficial del fabricante en Colombia → Open
Icecat → preguntarle al proveedor. Nunca al revés, y nunca "de memoria".

---

## Xiaomi: mi.com/co es la mejor fuente del catálogo

Es el hallazgo más útil de la corrida del 15/09/2026, y resolvió 39 de los 98
productos de esa lista. El sitio oficial de Xiaomi Colombia publica, en español,
y en una sola página por producto:

- la ficha técnica completa, con procesador, pantalla, cámaras, batería, red,
  resistencia y dimensiones;
- **la paleta oficial de colores**, que es justo lo que `colores.md` pide para
  cumplir la regla 13 sin inventar;
- las configuraciones de RAM y almacenamiento que el fabricante vende de verdad,
  que sirven para detectar variantes que el proveedor ofrece y no existen;
- el precio de lista oficial, útil como contraste del precio de mercado.

### Cómo se usa

**1. Cosechar los slugs, no adivinarlos.** Adivinar URLs gasta media docena de
404. Las listas de producto traen los enlaces reales:

```
https://www.mi.com/co/product-list/phone/       celulares, y de paso casi todo
https://www.mi.com/co/product-list/wearable/    relojes, bandas y audífonos
https://www.mi.com/co/product-list/accessory/   tablets
```

En cualquiera de ellas, los slugs salen del DOM:

```js
[...document.querySelectorAll('a[href*="/co/product/"]')]
  .map(a => a.getAttribute('href').match(/\/co\/product\/([^\/?#]+)/)?.[1])
```

**2. La ficha está en `/specs/`:** `https://www.mi.com/co/product/<slug>/specs/`.

**3. Extraer emparejando etiqueta y valor.** Las páginas de celulares ponen el
valor en la línea siguiente al encabezado, pero **las de wearables y accesorios
parten la etiqueta del valor** (`Tipo de carga:` en una línea, `carga magnética`
en la otra). Un extractor que solo mire encabezados devuelve secciones vacías.
La regla que funciona en los dos formatos: si una línea termina en `:`, únela
con la siguiente.

```js
const L = texto.split('\n').map(s => s.trim()).filter(Boolean);
const out = [];
for (let j = 0; j < L.length; j++) {
  if (/:$/.test(L[j]) && L[j + 1]) { out.push(L[j] + ' ' + L[j + 1]); j++; }
  else out.push(L[j]);
}
```

Recorta desde `Comprar ahora` hasta el pie de página (`Suscríbete`, `Síguenos`,
`Copyright`, `Compra y aprende`): lo de afuera es navegación.

### Lo que hay que mirar dos veces

- **La sección Xiaomi del proveedor mezcla Xiaomi y Redmi.** El parser ya corrige
  las referencias conocidas con `SUBMARCA_XIAOMI`; la tabla completa está en
  `titulos.md`. Ante una referencia nueva, el catálogo oficial manda.
- **Los colores a veces vienen en inglés** en las páginas de POCO
  (`Mint Green`, `Black`). Se traducen al publicar y queda anotado en
  `supuestos`.
- **Una página puede cubrir dos modelos.** El sitio describe el Redmi Note 15 y
  sus variantes en fichas separadas, pero Apple, por ejemplo, mete el iPhone 16
  y el 16 Plus en una sola: hay que separar los datos por modelo antes de
  publicar, o se publica el peso del equipo equivocado.
- **El catálogo vigente es el de hoy.** Si un modelo salió de línea, su página
  desaparece. Que no esté es información: ver "generación saliente" en
  `precios.md`.

---

## Open Icecat: bueno para lo que cubre, y hay que saber qué cubre

`scripts/icecat_local.py` trae fichas completas en español y las fotos en la
misma pasada. Cubrió 34 de 98 productos en la misma corrida.

### Cobertura real (medida el 15/09/2026)

| En el catálogo abierto | Solo en el catálogo de pago |
|---|---|
| Samsung, JBL, Motorola, Honor, Honor Choice, Lenovo, TCL, Nintendo, Sony Interactive Entertainment | **Apple, Xiaomi, Bose, Sony, realme** |

Las cinco de la derecha no son un error ni un problema de emparejamiento: su
contenido es de pago y el repositorio abierto responde *Access to this product
and language is restricted*. Para esas marcas, el camino es el sitio del
fabricante o el proveedor.

Como Xiaomi es la marca más grande de estas listas, **Icecat nunca va a cubrir
la mitad del catálogo**. Eso no es un fallo del paso: es la razón por la que
`preparar_fotos.py` descuenta lo resuelto y arma el pedido al proveedor.

### Las tres trampas del emparejamiento

1. **Trae la ficha de otra variante.** El índice mezcla capacidades y redes. En
   la corrida real, el A17 4G y el A17 5G emparejaron con *la misma* ficha, que
   era la del 4G y declara "5G: no compatible". La ficha sirve para lo que no
   cambia entre variantes —pantalla, cámara, batería—, y la memoria y la red se
   toman de la línea del proveedor. Siempre queda dicho en `supuestos`.
2. **Trae la ficha de otro producto.** El `Honor Pad X8b` (tablet de 11") emparejó
   con el `Honor X8b`, que es un **celular** de 6,7". Mismo nombre, producto
   distinto. Si la ficha contradice la categoría, se descarta entera.
3. **El nombre oficial no es el de la lista, ni el del emparejador.** Icecat
   llama `PartyBox Stage 320` a lo que la lista dice `PARTYBOX 320`, y
   `EXTREME 4/NO` a lo que la marca escribe `Xtreme 4`. La ficha corrige el
   nombre; el campo `modelo` de Icecat no siempre es publicable tal cual.

### Cuando `buscar` no encuentra lo que sí existe

Pasa seguido: el emparejador falla por diferencias de escritura, pero la
referencia está en el índice. En una corrida, siete productos que `buscar` dio
por perdidos aparecieron buscando a mano en el índice.

```bash
grep -i -E "^[0-9]+,JBL," icecat/indice.csv | grep -i -E "SB180|Xtreme 5|Encore"
```

Con el `icecat_id` en la mano, se edita `icecat/coincidencias.csv` —columnas
`icecat_id`, `modelo_icecat`, `puntaje`, `confirmado`— y se vuelve a correr
`traer`. Es el flujo previsto: la columna `confirmado` existe justamente para
que una persona la revise.

### Dos cosas operativas

- `indice` baja 291 MB y deja un CSV de ~338 MB. `buscar` lo carga entero en
  memoria: **tarda varios minutos y usa cerca de 3 GB**. No es que se haya
  colgado.
- `traer` limita a unas cuatro fotos por producto, que es el estándar de
  `imagenes.md`. Un producto con una sola foto en Icecat queda incompleto y pasa
  al pedido al proveedor.
- `preparar_fotos.py` busca lo ya resuelto en `fotos/urls-icecat.csv` **relativo
  al directorio actual**. Si el catálogo vive en `catalogo/`, hay que pasarle
  `--cubiertos catalogo/fotos/urls-icecat.csv` o reportará que no resolvió nada.

---

## Apple: casi no hay ficha que consultar

Apple Colombia (`apple.com/co`) publica la ficha de los modelos **de la línea
vigente**, en `/co/<modelo>/specs/`. Fuera de eso:

- **Los modelos que salieron de línea pierden la página.** `iPhone 16e` y
  `iPhone 15 Pro Max` redirigen al listado general. Que redirija es, otra vez,
  información de negocio: el proveedor está ofreciendo generación saliente.
- **No hay tienda en línea de Apple en Colombia**, solo un buscador de
  distribuidores. Por eso no hay página oficial de los adaptadores de 20 W y
  40 W ni de accesorios sueltos.
- La ficha del iPhone suele cubrir **el modelo y su Plus en la misma página**:
  separar los datos antes de publicar.

Para Apple, entonces, lo normal es pedirle la ficha y las fotos al proveedor.

---

## Dominios que hay que autorizar en la extensión del navegador

La extensión pide permiso por sitio. En la corrida del 15/09/2026 estaban
autorizados `mi.com`, `apple.com` y `alkosto.com`, y **no** lo estaban
`samsung.com`, `honor.com`, `realme.com`, `playstation.com`, `bose.com`,
`jbl.com` ni `ktronix.com`. Eso dejó once productos sin ficha oficial.

Conviene pedir los permisos **antes** de arrancar el paso 4, mirando qué marcas
trae la lista. Un dominio sin autorizar corta el lote entero: `browser_batch`
se detiene en el primer error.

---

## Lo que no se inventa nunca

- **La garantía.** El plazo y el procedimiento son un dato del negocio, no del
  fabricante. La ficha dice que aplica la garantía legal que la ley colombiana
  reconoce para bienes nuevos y remite a la política de garantías de la tienda.
  Poner un número inventado es peor que no ponerlo, y un marcador `[[ ]]` en un
  texto publicado está prohibido por la regla 4 del proyecto.
- **El contenido de la caja.** Casi ninguna ficha lo detalla. Si se toma del
  estándar de la línea, se dice en `supuestos` y se confirma con el proveedor
  antes de prometerlo. Lo que sí se afirma cuando consta: que Samsung y Apple no
  incluyen cargador, o que Xiaomi incluye uno de 15 W aunque el equipo admita
  18 W.
- **Lo que la ficha no declara.** Si el fabricante no publica autonomía, no se
  promete autonomía. Si no declara resistencia al agua, no se menciona. Decirlo
  explícitamente en "Garantía y notas" es mejor que callarlo: evita que alguien
  lo agregue después "porque seguro tiene".
