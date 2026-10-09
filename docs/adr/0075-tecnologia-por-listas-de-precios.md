# ADR-0075 — La tecnología entra por la lista de precios, por modelo

**Fecha:** 2026-10-08
**Estado:** aceptado. Extiende ADR-0067 (proveedores por WhatsApp), que solo
contemplaba bolsos y ropa por la exportación del chat.

## Contexto

El proveedor de tecnología no publica fotos con precio como el de prendas: manda
una lista de texto con decenas de modelos, configuraciones (memoria,
almacenamiento, SIM), costo y los colores que tiene, marcados con emojis. La
skill `listas-de-proveedor` ya la parsea, la compara con la anterior, investiga
cada modelo nuevo —ficha del sitio de la marca, Icecat como segunda fuente,
precio de mercado— y deja una carpeta por modelo con la ficha y las fotos
(`catalogo/entregables/fichas/<Modelo>/`).

Lo que faltaba era el último tramo: que la lista mueva el catálogo sin que una
persona cargue a mano cada variante, y sin que la lista decida lo que no le toca.

Decisiones del negocio del 08/10/2026:

- **Se vende con lo que el proveedor dice tener**, y la lista mueve **solo la
  disponibilidad y el costo**. El precio de venta, la ficha y las fotos son del
  panel.
- **Existencia: 2 unidades por variante**, repuestas con cada lista.
- El precio de mercado de una lista vale **7 días**.
- El plazo con que el proveedor entrega **queda por definir**: ningún texto
  promete uno concreto mientras tanto.

## Decisión

1. **La identidad es el modelo, no la configuración.** El producto es «Samsung
   Galaxy A17 5G» y su huella es `HuellaProveedor.deModelo(proveedor, idModelo)`:
   el id que decide la skill, **sin precio**, porque el costo cambia con cada
   lista y el modelo no. Las prendas siguen con su huella de título y precio.
2. **La variante es configuración × color.** Atributos RAM, Almacenamiento, SIM
   (sembrados en V92) y Color. El SKU es el comienzo del id de configuración más
   ocho caracteres del SHA-256 de configuración y color: entero no cabe en los
   60 de la columna. `variante_de_proveedor` guarda de qué configuración sale
   cada variante y su costo de hoy; el costo nunca sale al comprador.
3. **Un borrador propio, no el de prendas.** `BorradorTecnologia` sale de una
   lista ya procesada, no de una extracción con un modelo de lenguaje, y lo que
   se decide es otra cosa: qué colores de la paleta oficial se venden de cada
   configuración y a qué precio. Comparte el destino —un `Producto` de
   proveedor— y los estados. Uno en revisión por modelo y proveedor: la lista
   siguiente actualiza ese y conserva lo elegido.
4. **Qué hace una lista** (`ImportarListaDeTecnologia`, en una transacción):
   - modelo que ya se vende → se renueva, cada configuración que vino actualiza
     su costo y su existencia **libre** vuelve a 2 por color —solo los colores
     que la lista dice tener, si los dice; el que no viene queda en 0—; las
     configuraciones que el producto no tiene van a un borrador de ese producto;
   - modelo que no se vende → su borrador en revisión, sin lo que alguien ya
     rechazó o dejó sin colores al aprobar;
   - configuración desaparecida → pierde lo libre, **nunca lo reservado**;
   - modelo desaparecido → agotado por el proveedor.
   Qué desapareció lo decide la skill y no la API: solo ella sabe qué bloques
   trae la lista, y una lista parcial no dice que lo que falta se acabó.
   Los libros de inventario se bloquean en orden de id de variante.
   **Una lista entra una vez y nunca hacia atrás** (`lista_tecnologia_importada`):
   la misma otra vez repondría lo vendido entre las dos, y una más vieja
   desharía la de hoy. Una corregida el mismo día, con otro contenido, sí entra.
5. **La tecnología vence con su propia ventana**, 7 días
   (`PROVEEDORES_TECNOLOGIA_VENTANA_DISPONIBILIDAD`): con los 3 días de los
   mensajes, todo lo de una lista se ocultaría antes de la siguiente.
6. **Los colores arrancan con lo que sugiere la lista y nada más.** Vender un
   color que el proveedor no dijo tener es lo que la revisión existe para evitar.
7. **El producto nace en borrador y visto en la fecha de la lista.** Las fotos
   las sube `tools/importar-lista-tecnologia.mjs --fotos` desde la carpeta del
   modelo, y sin imagen principal no se publica. Con la fecha de la aprobación,
   la lista de ese mismo día quedaba más vieja que el producto y no podía
   agotarlo ni renovarlo.
8. **Las fotos son de referencia, y se dice.** Son del modelo, no de cada color
   ni de la unidad: la ficha de un producto de tecnología lo avisa, y los
   términos (numerales 4 y 13, versión `2026-10-08.3`) dicen que las de ropa,
   calzado y bolsos muestran el producto —tomadas por nosotros o por el
   proveedor— y que las de tecnología son de referencia, y que lo que no es
   nuestro pertenece a sus titulares.
9. **Un proveedor es de una cosa o de la otra.** `Proveedor.entraPorExportacion()`:
   bolsos y ropa por el chat (`PROVEEDOR_DE_LISTAS` si no), tecnología por la
   lista (`PROVEEDOR_SIN_LISTAS` si no).

## Consecuencias

- El flujo de una lista es: skill (pasos 1-6) → `exportar_lista.py` →
  `importar-lista-tecnologia.mjs` → revisión en `/admin/tecnologia` →
  `importar-lista-tecnologia.mjs --fotos --publicar`.
- **Los colores que se venden se fijan al aprobar, y la lista los enciende o los
  apaga.** Si la lista de mañana dice azul y no negro, el negro queda sin
  existencia libre. Si no dice colores, repone todos: no hay con qué retirar
  uno. Un color nuevo de una configuración que ya se vende no se propone solo:
  la importación lo informa (`coloresSinVariante`) y se añade desde el panel.
- **La lista no mueve el precio de venta.** Avisa cuando el costo nuevo alcanza
  el precio (`sinMargen`), y alguien decide. Aprobar exige un precio que supere
  el costo.
- Un modelo rechazado no vuelve a proponerse con las mismas configuraciones; una
  configuración nueva del mismo modelo sí.
- El uso de las fotos y fichas de las marcas y de Icecat queda como punto para
  el abogado (`docs/14`, numeral 8).
