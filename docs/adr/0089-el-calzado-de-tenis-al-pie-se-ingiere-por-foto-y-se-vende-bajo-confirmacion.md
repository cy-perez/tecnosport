# ADR-0089: el calzado de Tenis Al Pie se ingiere por foto y se vende bajo confirmación

Fecha: 2026-10-25
Estado: aceptada; las decisiones 5 y 7 las cambia `ADR-0090`, y el filtro de las fotos de las
decisiones 4 y 5, `ADR-0091` (26 de octubre de 2026)
Amplía: `ADR-0067` (ingesta por exportación), `ADR-0078` y `ADR-0084` (el reparto de las fotos),
`ADR-0085` (las tallas), `ADR-0066` (la ventana de disponibilidad) y `ADR-0086` (la confirmación
por WhatsApp con enlace de compra).

## Contexto

Tenis Al Pie es el proveedor principal de calzado. Su chat del 14 al 23 de octubre de 2026 trae
195 fotos y casi nada de texto, y no se parece al de ningún otro proveedor:

- **Manda tandas de 25 a 35 modelos distintos** —Nike, Adidas, New Balance, Jordan, Puma, y
  también Louis Vuitton, Dior o McQueen— **sin SKU, sin marca ni modelo escritos**, y un solo
  precio al final: «Detal :$180.000 / X mayor 💰135». Con la regla de `ADR-0078` —sin SKU no se
  parte— la tanda entera quedaba en un borrador.
- **El precio que paga la tienda es el «por mayor»**, escrito en miles detrás de 💰. El patrón
  tomaba el primero, el de detal, y «💰135» no lo leía.
- **Las tallas no van en el anuncio**: el proveedor las recuerda aparte, «Tallas hombre Eu 40 41 42
  43 44» y «Talla dama Eu 36 37 38 39», y valen para todo lo que manda.
- **No dice qué tallas tiene de cada modelo**, ni existencias.
- **En las mismas tandas vienen Crocs, chanclas y botas**, que no se venden, y muchas fotos son del
  tenis puesto o ambientado con utilería de otras marcas.
- **Manda tandas cada dos o tres días**: con la ventana de tres, un modelo que se saltaba una se
  ocultaba.
- **Algunas tandas no traen precio.**

## Decisión

Decidida por el negocio el 25 de octubre de 2026.

1. **El precio por mayor manda** (`PatronDePrecio`). Si el texto rotula algún precio «x mayor» o
   «por mayor», solo cuentan los rotulados, y detrás del rótulo dos o tres cifras se leen en miles.
   El margen es el 20 % sobre ese costo —el factor 1,20 del proveedor, en el panel—, con los topes
   de ganancia de siempre. El 20 % es bruto: la comisión de las pasarelas y el recaudo de
   contraentrega quedan por definir.
2. **Una tanda sin precio se descarta**, que es lo que ya hacía la agrupación: sin un precio no
   abre publicación.
3. **Cuatro ajustes de ingesta en el proveedor** (`AjustesDeIngesta`, `V118`), que el panel guarda:
   - una foto, un producto;
   - venta bajo confirmación;
   - días de vigencia propios: cinco para Tenis Al Pie;
   - tallas por género, como las nombra el proveedor, con su ajuste: dama 36–39 y caballero 40–44,
     ajuste 1. Se vende en talla nacional (`ADR-0085`): dama 35–38, caballero 39–43, y unisex de la
     35 a la 43.
4. **El lector de fotos lee el calzado** (`LecturaDeCalzado`): el tipo —tenis, chancla, sandalia,
   zueco, bota—, el modelo con su seguridad, el género —dama, caballero, unisex— y si el tenis se ve
   solo. Se ve solo sobre una superficie o en la mano; no, puesto, con pies o piernas, en una
   captura de otra aplicación o con utilería de otras marcas.
5. **Cada foto es un producto** (`RepartoDeFotos.unaPorFoto`), con la foto como exclusiva para la
   huella visual: la misma foto en otra tanda reconoce el producto. **Solo pasan los tenis que se
   ven solos**: la Crocs, la chancla, la sandalia, la bota, el tenis puesto y la caja vacía no llegan
   a borrador. Los colores del mismo modelo se juntan en el panel con `UnirBorradores`; la etiqueta
   de diseño —marca y modelo— dice cuáles son candidatos. Si el extractor no ve producto en un texto
   que es solo el precio, la tanda se parte igual desde un producto con ese precio.
6. **El género decide la categoría y las tallas.** El borrador guarda el género (`V119`) y el panel
   propone Calzado › Dama, Caballero o Unisex. Sin género leído, unisex.
7. **Todo el calzado se titula «Tenis importados estilo &lt;Marca&gt; &lt;Modelo&gt;»**
   (`TituloDeTenis`), sin el género, que queda en la categoría. El modelo de una réplica es una
   suposición del lector y los parecidos se confunden —un Jordan 3 y un Jordan 4—: solo va al título
   el que el lector da por seguro; si no, «Tenis importados estilo Nike», y quien aprueba lo
   confirma. Lo ya publicado se retitula (`V121`), sin cambiar el slug.
8. **Se vende como la tecnología** (`ADR-0086`): cada talla nace **por confirmar**, la ficha
   pregunta por WhatsApp «¿Tienen esta talla disponible?», y el enlace de compra de 24 horas aparta
   la unidad. La marca de la variante pasa de «color por confirmar» a «disponibilidad por
   confirmar» (`V120`), y su excepción a `DisponibilidadPorConfirmarException`; lo que se pregunta
   lo dice la línea del producto. La existencia es nominal, la misma de la tecnología, y se repone
   cuando el modelo vuelve a aparecer (`ExistenciaBajoConfirmacion`); nunca baja.
9. **El envío se cotiza con el promedio de peso** que el panel tiene para la categoría (`ADR-0071`).
10. **El retracto no cambia para el comprador.** El proveedor cambia un par por otra talla, pero no
    recibe un retracto: ese par se lo queda la tienda, y es un costo del negocio. El transporte de la
    devolución sigue a cargo del comprador, como dicen los términos (numeral 9).

## Consecuencias

- **Los textos legales subieron a `2026-10-25.2`**: el numeral 5 de los términos y los numerales 5
  y 7 de la política de datos cubren la talla, con la skill `textos-legales-comerciales`.
- **Las réplicas de marcas registradas quedan para el abogado** (`docs/14`, punto 10): el título
  dice «estilo», pero el logo está en el tenis. Y las condiciones de Wompi sobre productos
  prohibidos no se han leído.
- **El esquema del lector cambió**: `LectorDeFotosClaudeContratoTest` contra la API real se corre
  antes de fusionar (`-PintegracionExterna=true` con `ANTHROPIC_API_KEY`). El lector tiene ahora
  8192 tokens y 150 segundos: una tanda de 35 fotos con el calzado no cabía en 4096.
- **El título del calzado pierde lo descriptivo**: «Tenis para caballero tipo media ultraliviano»
  de Imperio Wicho es ahora «Tenis importados». Es la forma que decidió el negocio.
- **Las lecturas guardadas antes no traen el calzado**: un borrador de antes no se recalcula.
- **No hay guía de tallas.** El comprador ve la talla nacional sin la equivalencia EUR, y una talla
  equivocada termina en un cambio o en un retracto.
- **Las fotos son del proveedor**, y algunas las tomó él de otros vendedores. Una foto propia de
  los pares comprados es la que se puede retocar con `fotos-estudio-degradado` y la que sirve para el
  visor 360.
