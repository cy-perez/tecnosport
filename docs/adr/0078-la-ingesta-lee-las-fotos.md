# ADR-0078: la ingesta lee las fotos

Fecha: 2026-10-10
Estado: aceptada
Amplía: `ADR-0067` (ingesta por exportación), que decía «un texto corto y un puñado de fotos»
cuando el extractor solo recibía el texto. Y `ADR-0070` (una variante es una prenda), cuyas
prendas ahora llegan propuestas.

## Contexto

Las exportaciones del 9 de octubre de 2026 de La Riverah y Violeta se revisaron foto por foto (185
fotos). Lo que no funcionaba estaba en las fotos, y el extractor no veía ninguna.

**Violeta imprime la referencia en cada foto**: «C:261002 J:VY3026» en la que muestra la camiseta y
el jogger juntos, «VY3010» en la del bodi solo. La letra es la inicial del tipo. Con eso se sabe sin
adivinar cuál foto es de cuál producto. Hasta hoy todas las fotos eran de todos, con
`FOTOS_COMPARTIDAS`. Sus colores llegan de cuatro maneras: una foto con todos los tonos de frente y
otra de espalda, los tonos repartidos en dos fotos, una foto por tono más una consolidada, o una
foto por tono con frente y espalda juntas. El texto casi nunca los nombra. Y a veces reparte las
tallas por tono —«Talla SM ML(negro) / Talla ML(cocoa) / Talla SM(verde)»—, con lo que la aprobación,
que hacía tono × talla, publicaba un cocoa SM que no existe.

**La Riverah publica catálogos.** Siete publicaciones del día son un texto general —«Camisetas
oversize para caballero»— y de diez a veintitrés fotos, cada una de un diseño distinto, con un pie
impreso que dice las tallas y el SKU de ese diseño. Salían como un borrador con veintitrés fotos,
cuando el tope es nueve. Algunas fotos son el mismo diseño en otro color, otras traen dos SKU con
dos fechas (el mismo diseño publicado otra vez), y la fecha impresa no es la del mensaje. Además,
casi cada anuncio trae precios que no son del producto: la gorra que acompaña la camiseta en las
fotos, el «set falda + básica», el dúo, la promoción por cantidad.

## Decisión

1. **Un lector de fotos aparte del extractor** (`LectorDeFotos`, `LectorDeFotosClaude`). Tiene su
   propio prompt y su propio esquema (`ia/lector-fotos/`) y la misma API, clave y reintentos que el
   extractor (`LlamadaAClaude`). Se llama después de leer el texto, con el anuncio, los productos
   que el extractor vio en él y las fotos achicadas a 1024 px en JPEG. Por cada foto devuelve:
   - las referencias impresas,
   - el SKU y las tallas del pie,
   - los colores de lo que se vende (no de lo que ambienta),
   - una etiqueta de diseño.

   Va aparte porque falla aparte: si la lectura falla, la publicación sigue como antes —todas las
   fotos para todos— y no se pierde. Eso vale para cualquier fallo, no solo los del lector: el
   bucket que no responde a mitad de la lectura tampoco sale de ahí. Si saliera, cerraría el lote
   entero (lo encontró la revisión del 10 de octubre de 2026). Se apaga sin desplegar con
   `PROVEEDORES_LECTURA_FOTOS_HABILITADA`, y sin clave queda apagado igual.
2. **El reparto lo decide el dominio, no el modelo** (`RepartoDeFotos`). Al lector no se le cree sin
   respaldo:
   - **Varios productos con código.** Una foto va al producto cuya referencia impresa es la del
     texto. La que muestra varias es de todas, y la que no muestra ninguna también. Si a un
     producto no le toca ninguna foto, se queda con todas.
   - **Un texto y un álbum de diseños.** Cada grupo de fotos del mismo diseño es un producto, con
     el texto y el precio del anuncio y las tallas del pie. Estos productos no cuentan para el tope
     de cinco del 2 de octubre, que es de productos escritos en el texto. Además:
     - El SKU del pie es su referencia solo si todas sus fotos traen el mismo: la falda con un SKU
       por color no tiene una referencia.
     - Un diseño no hereda el código que el texto le dio al anuncio, y un SKU repetido en dos
       diseños no identifica a ninguno. Con la misma referencia, los diseños 2 a N se descartaban
       y sus fotos quedaban perdidas en el primero.
     - Una foto que el lector no leyó —sin archivo, ilegible, o que el modelo no devolvió— no es
       un diseño: es de todos. Antes salía como un borrador basura.
     - Un diseño que el lector separó sin ningún pie impreso lleva `CONFIANZA_BAJA`.
     - **Dos fotos que el lector juntó se separan si muestran el mismo color con SKU distintos**:
       dos vistas de la misma prenda no llevan dos SKU. La primera corrida contra la verdad lo
       mostró: el lector juntaba los diez jeans azules de las 19:19 en tres «diseños». Sin SKU
       no se separa nada.
     - **Del pie con dos bloques vale el de la fecha más reciente**, y lo decide el dominio: el
       lector devuelve todos los bloques. Cuando se lo pedíamos al modelo, tomó el SKU viejo en
       las trece fotos de un álbum.
   - **Una foto de un solo color** sugiere ese color. El panel lo propone al aprobar, y las fotos
     del mismo color quedan como una prenda. La consolidada no sugiere nada: vale para todas.
3. **Las fotos ajenas nacen descartadas** en el borrador (`fotos_descartadas`): el mismo mecanismo
   con que quien revisa saca una foto. El producto repartido con una foto exclusiva deja de llevar
   `FOTOS_COMPARTIDAS`, y esa foto da su huella visual. Como esa foto ya no es siempre la primera
   de la publicación, **descartar cualquier foto olvida la huella visual**, que se vuelve a tomar
   de la principal al aprobar.
4. **Un diseño sin código tiene su propia huella** (`HuellaProveedor.deDisenoDeAnuncio`): la del
   anuncio más la huella visual de su foto. Con la del anuncio, los trece diseños eran el mismo
   producto y aprobar el segundo chocaba con el primero. Por la misma razón, los anuncios en
   revisión ya no comparan las fotos que su borrador descartó. Si no, el segundo diseño se
   descartaba por «anuncio repetido» del primero.
5. **Las tallas por tono** las lee el extractor del texto, solo para los tonos que el texto nombra
   (`TallasPorTono`). La aprobación crea cada tono solo en las suyas. El tono se busca por el
   nombre de la paleta con que se aprueba **y por el color que la lectura vio en sus fotos**,
   porque la paleta no tiene «cocoa» y quien aprueba la marca «Café».
6. **Los precios que no son del producto** —acompañantes, combos y promociones por cantidad— van
   en `precios_adicionales` y el panel solo los muestra. No son productos: no tienen foto ni código,
   y el catálogo no tiene combos. Decidido al ejecutar el plan del 10 de octubre de 2026, con las
   opciones que el plan recomendaba.
7. **Partir un borrador** desde el panel es la salida cuando el lector juntó dos productos en uno:
   - las fotos elegidas se van a un borrador nuevo de la misma publicación, con los mismos datos y
     una huella derivada (`HuellaProveedor.deParte`);
   - en el de origen esas fotos quedan descartadas;
   - el nuevo conserva las alertas del origen, `SIN_FOTOS` incluida: si el origen la llevaba,
     ninguna de las fotos que se van tiene archivo.
8. El prompt del extractor dice además que lo que el proveedor escribe de cambios y garantías («la
   ropa americana no tiene garantía ni cambio») no va en la descripción. Eso es entre el proveedor y
   la tienda, y al cliente lo cubre la garantía legal.

## Consecuencias

- Una llamada más por publicación con fotos. Un álbum de veintitrés fotos son del orden de 25 mil
  tokens de entrada con Haiku 4.5. La tarifa no se verificó al escribir esto: hay que mirarla en la
  consola antes de dar una cifra de costo.
- **Lo que las pruebas del pipeline no ven es si el modelo lee bien.** Eso lo mide
  `IngestaRealContraVerdadTest` (`integracion-externa`), que pasa los zips de `ingesta/` por el
  código real y los compara con `ingesta/verdad.json`, escrito a mano a partir de las fotos. Ni los
  zips ni la verdad se versionan.
- Lo que el reparto todavía no hace:
  - **Una foto de otra publicación no se suma a un borrador que ya existe.** La camiseta 261002 de
    Violeta sale como acompañante en dos publicaciones, y la segunda se descarta por la misma
    referencia en revisión, como antes. Sumarla obligaría a que un borrador tenga fotos de dos
    publicaciones.
  - **Mover una foto entre dos borradores ya existentes** no está: está partir.
