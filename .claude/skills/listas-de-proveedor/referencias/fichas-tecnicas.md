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

**1. Comprobar el slug, no cosecharlo.** Las rutas de mi.com cambian: las de
2026-09-15 (`/co/product-list/phone/`, `/wearable/`, `/accessory/`) **devuelven
404** desde el 19/09/2026. Ahora el catálogo está partido por línea:

```
https://www.mi.com/co/product-list/phone/xiaomi/   Xiaomi
https://www.mi.com/co/product-list/phone/redmi/    Redmi
https://www.mi.com/co/poco/                        POCO
https://www.mi.com/co/wearables/                   relojes, bandas y audífonos
https://www.mi.com/co/product-list/tablets/tablet/ tablets
```

Y cosechar slugs de esas páginas **no funciona bien**: los enlaces de producto
salen del menú de navegación, no del listado, así que se recogen 67 slugs que no
son los que buscas. Conviene al revés: **construir el slug desde el título y
comprobarlo**, que acierta casi siempre porque el patrón es regular
(`redmi-note-15-pro`, `poco-x8-pro-max`, `redmi-pad-2-9-7-inch`,
`xiaomi-smart-projector-l1`).

**Cuidado con el 404 blando: el estado HTTP no sirve.** mi.com responde **200 a
cualquier slug**, incluido uno inventado, y redirige por dentro a
`/co/errors/404`. Lo que distingue un producto real es la **URL final**:

```js
const r = await fetch('/co/product/' + slug + '/specs/');
const existe = !r.url.includes('/errors/404');   // NO mirar r.status
```

Comprobado así, las 26 referencias Xiaomi de la lista del 12/09/2026 tenían
ficha, incluidas las que el listado no mostraba.

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

`scripts/icecat_local.py` trae fichas completas en español. Cubrió 34 de 98
productos en la misma corrida.

Traía también los enlaces de las fotos, y esa parte ya no se usa: las fotos
salieron del flujo el 25/09/2026 (regla 16 del `SKILL.md`). Lo que se conserva
de este paso es la ficha.

### Cobertura real

Medida dos veces, con listas parecidas y resultado parecido:

| | 15/09/2026 | 19/09/2026 |
|---|--:|--:|
| Productos de la lista | 98 | 96 |
| Resueltos por `mi.com/co` | 39 | 38 |
| Fichas traídas de Open Icecat | 34 | 34 |
| Sin ficha de ninguna de las dos | — | 16 |

La proporción se repite: **Icecat cubre un tercio largo y Xiaomi otro tercio**,
y queda un resto que solo resuelve el proveedor. No es un fallo del paso: a ese
resto se le escribe una descripción corta con lo que el nombre comercial
establece y una nota de qué falta, y el dato se le pide al proveedor.

### Cobertura por marca (medida el 15/09/2026)

| En el catálogo abierto | Solo en el catálogo de pago |
|---|---|
| Samsung, JBL, Motorola, Honor, Honor Choice, Lenovo, TCL, Nintendo, Sony Interactive Entertainment | **Apple, Xiaomi, Bose, Sony, realme** |

Las cinco de la derecha no son un error ni un problema de emparejamiento: su
contenido es de pago y el repositorio abierto responde *Access to this product
and language is restricted*. Para esas marcas, el camino es el sitio del
fabricante o el proveedor.

Como Xiaomi es la marca más grande de estas listas, **Icecat nunca va a cubrir
la mitad del catálogo**. Eso no es un fallo del paso: es la razón por la que
`mi.com/co` y el proveedor son fuentes de primera, no de respaldo.

### Las cuatro trampas del emparejamiento

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
4. **Trae la ficha del juego, no la de la consola.** El bundle
   `Nintendo Switch 2 Mario Kart` empareja con `Nintendo Mario Kart World
   (Switch 2)`, que **pesa 10 gramos**: es el juego. Es la misma trampa que
   tiende el buscador de Alkosto con esa referencia. El peso es la señal más
   rápida para detectarla.

Cuando la ficha contradiga la categoría, el peso o el tamaño del producto, se
descarta entera con `"icecat": false` en `prosa.json`. No se le saca «lo que
sirva»: si la ficha es de otro producto, todos sus datos son de otro producto.

### Lo que Icecat trae vacío

`icecat_local.py traer` deja un JSON por producto con `especificaciones` —unos
115 pares atributo/valor, que es de donde sale la tabla—, `vinetas`, `resumen`
y `atribucion`. Pero **`meta_titulo`, `meta_descripcion`, `garantia` y
`descripcion_larga` vienen vacíos** en todas las fichas medidas. Esos cuatro los
escribe una persona; no los esperes del catálogo.

### La memoria nunca sale de la ficha

Es la consecuencia práctica de la trampa 1, y `redactar_fichas.py` ya la aplica:
la fila «Memoria» de la tabla se arma **siempre** con la RAM y el
almacenamiento de la línea del proveedor. La ficha del Galaxy A56 declara 128GB
y vendemos 256; la del A57 declara 8GB y el nuestro trae 12. Copiar la ficha tal
cual publica una tabla que contradice el título del propio producto.

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
- `traer` escribe además un `fotos/urls-icecat.csv` con enlaces de fotos. Es un
  sobrante de cuando la skill las bajaba: nadie lo lee. Si algún día vuelven las
  fotos, ojo con que `preparar_fotos.py` lo busca **relativo al directorio
  actual**, así que con el catálogo en `catalogo/` hay que pasarle
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

Para Apple, entonces, lo normal es pedirle la ficha al proveedor.

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
