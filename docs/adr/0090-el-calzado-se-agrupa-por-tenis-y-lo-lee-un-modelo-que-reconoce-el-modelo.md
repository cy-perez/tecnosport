# ADR-0090: el calzado se agrupa por tenis y lo lee un modelo que reconoce el modelo

Fecha: 2026-10-26
Estado: aceptada; la decisión 6 la cambia `ADR-0091` el mismo día
Cambia: `ADR-0089`, decisiones 5 (una foto, un producto) y 7 (solo el modelo seguro va al título).
Amplía: `ADR-0078` (el lector de fotos).

## Contexto

La primera ingesta de Tenis Al Pie en dev, la del zip del 14 al 23 de octubre de 2026 (195 fotos),
dejó 41 borradores. Revisándolos:

- **El mismo tenis en varias fotos quedó en borradores aparte.** El Shox R4 en negro, plata y gris
  eran cinco borradores de la misma tanda; el Le Coq Sportif, tres; el Alexander McQueen, dos, con
  la misma toma en otro encuadre y un rótulo «662-663» encima. La huella visual no los junta: entre
  los dos encuadres hay 20 bits de distancia contra un umbral de 6, y además solo compara contra
  productos ya aprobados.
- **La pista del panel para unirlos no servía.** «Posible mismo diseño» sale de la etiqueta de texto
  libre del lector, y para los tenis sin modelo reconocido era tan general que juntaba un McQueen,
  un Dior, un Off-White y un Nike en un grupo de ocho, y le sumaba un Air Max a los Le Coq.
- **Casi ningún título tenía modelo.** De 39 borradores en revisión, 15 decían «Tenis importados»
  sin marca, 17 llevaban la marca sola y 7 el modelo. Tres causas: el lector era Haiku 4.5, que no
  reconoce bien un modelo por la forma; el prompt solo dejaba anotar la marca que «se lee», y el
  gallo de Le Coq Sportif o un McQueen sin texto legible no se leen; y el prompt ponía «Shox TL o
  Shox R4» de ejemplo de duda, justo el que vino.
- **El filtro de lo que no se publica dejó pasar** una foto con un libro Cartier, otra con una bolsa
  Dior, una captura de una historia de Instagram y varias con el tenis puesto.

## Decisión

Decidida por el negocio el 26 de octubre de 2026.

1. **Un producto por tenis, no por foto.** El lector ve la tanda entera de una vez y dice, de cada
   foto, cuál es la primera de la tanda que muestra ese mismo tenis en cualquier color
   (`mismo_tenis_que`). Las fotos que apuntan a la misma, directa o en cadena, son un borrador, y
   cada una aporta su color (`RepartoDeFotos.porTenis`). Es lo que `ADR-0078` no quiso hacer con la
   etiqueta de diseño, que cambiaba por un detalle; aquí el lector no inventa un nombre sino que
   señala otra foto que tiene delante. Si junta de más, el panel tiene «Partir».
2. **La marca por parecido.** El lector dice a qué marca se parece el tenis aunque no se lea
   escrita —el swoosh, el Jumpman, el gallo, la suela del Oversized de McQueen—, y esa es la del
   título. `marcas`, lo que se lee, sigue sirviendo para repartir en los demás proveedores.
3. **El modelo va al título aunque el lector dude** (`TituloDeTenis`), y el borrador lleva
   `MODELO_POR_CONFIRMAR`: quien aprueba lo confirma en las fotos o lo corrige. El prompt ya no pone
   pares de ejemplo de duda, sino el rasgo que los distingue —las columnas del Shox R4 solo en el
   talón, las alas de los cordones del Jordan 4—.
4. **Un lector propio para el calzado**: Sonnet 5.5 con esfuerzo `low`
   (`tecnosport.proveedores.lectura-fotos.calzado`), para las fotos de todo proveedor de la línea
   de calzado. La ropa y los bolsos siguen con Haiku 4.5. Sonnet 5.5 cuesta el doble por
   token que Haiku 4.5, razona siempre y rechaza una temperatura distinta de la de siempre, así que
   a este lector se le pide el esfuerzo y no la temperatura, y la respuesta se lee del bloque de
   texto, que ya no es el primero.
5. **La etiqueta de diseño de un tenis es su marca y su modelo**, o ninguna: sin modelo no hay
   pista de «posible mismo diseño».
6. **El filtro de lo que no se publica nombra los casos que se colaron**: la captura de pantalla
   por su barra de estado y sus botones, la utilería de otra marca aunque el tenis esté en la mano,
   las medias y el pantalón.
7. **El mismo título para el tenis de cualquier proveedor**
   (`ProductoExtraido.conElTenisDeSusFotos`). Imperio Wicho escribe un texto por anuncio, y de
   «NIKE UNISEX ULTRALIVIANO» salía «Tenis importados estilo Nike». Ahora:
   - si el texto ya nombra el modelo, manda el texto: lo escribió quien tiene el tenis en la mano;
   - si nombra solo la marca, la marca se queda y el modelo sale de las fotos;
   - si no nombra marca, va la que el lector reconoce, o las que se leen escritas.

   Solo cuando las fotos son del producto: con varios productos en el anuncio, la marca de una foto
   puede ser del otro. El género y las tallas siguen saliendo del texto, como antes.

## Consecuencias

- **El experimento antes de fiarse.** `LecturaDeCalzadoComparadaTest` corre la ingesta de verdad del
  zip con Haiku y con Sonnet y deja lado a lado los títulos, los grupos y las fotos descartadas.
  Pide la clave y el zip en `ingesta/`; no corre en el pipeline. Cambiar de modelo o de esfuerzo es
  una variable de entorno, sin desplegar código.
- **El tiempo de la lectura.** El timeout es el mismo de 150 segundos, para que cuatro intentos
  sigan cabiendo en el tramo de 10 minutos (`ADR-0080`). Con el esfuerzo `low` el razonamiento es
  corto; si una tanda de 35 fotos no cabe, el experimento lo muestra antes que la ingesta.
- **Un modelo dudoso en el título es información falsa para quien compra** (Ley 1480, art. 23) si
  nadie lo confirma. La alerta lo hace visible; la responsabilidad queda en la aprobación.
- **La huella visual de un tenis es su primera foto.** La tanda siguiente lo reconoce si trae esa
  misma foto; si solo trae otro color, sale un borrador nuevo, como pasaba con una foto por producto.
- **Las lecturas guardadas no se recalculan** (no traen el grupo): los borradores de la ingesta del
  25 de octubre siguen uno por foto. Para verlos agrupados hay que purgarlos y volver a ingerir.
- **Una marca que solo se ve en la foto ahora alerta la réplica, en los tenis.** El 11 de octubre de
  2026 se decidió lo contrario para todo el catálogo: un logo leído en la foto no la encendía,
  porque no decía que el proveedor la vendiera como réplica. En un tenis esa marca ahora va al
  título con «estilo», y un título así se publica con la marca Genérica (`ADR-0089`): la alerta es
  la que se lo recuerda a quien aprueba. La ropa sigue con la regla del 11 de octubre.
- **Las fotos de la línea de calzado cuestan el doble por token** que con Haiku, también las de
  Imperio Wicho.
- **El esquema del lector cambió**: `LectorDeFotosClaudeContratoTest` contra la API real se corre
  antes de fusionar, ahora también con el lector de calzado.
