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

   La petición va con `temperature: 0`: sin ella, el lector leyó distinto las mismas fotos en
   corridas seguidas. Haiku 4.5 admite los parámetros de muestreo; los modelos desde Opus 4.7 los
   rechazan con un 400, así que cambiar de modelo obliga a revisarlo.

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
   - **Un texto y un álbum (La Riverah): un producto por SKU** (decidido el 10 de octubre de
     2026). Cada SKU del pie impreso es una prenda que el proveedor vende aparte, con sus propias
     tallas, y es lo único del álbum que no cambia de una corrida del lector a otra. Primero se
     agrupó por la etiqueta de diseño del lector, para que un diseño en varios colores fuera un
     producto. Tres corridas seguidas contra `ingesta/verdad.json` mostraron que el lector junta
     y separa distinto los mismos jeans y bermudas cada vez: los diez jeans de las 19:19 salieron
     como 3, como 1 y como 10 productos. El costo de esta regla: un diseño en seis colores con un
     SKU por color son seis productos, y una falda con un SKU por color son dos.
     - Las fotos sin SKU se agrupan por la etiqueta del lector y llevan `CONFIANZA_BAJA`.
     - Dos SKU distintos impresos hacen álbum aunque el lector diga que no lo es.
     - Un producto no hereda el código que el texto le dio al anuncio.
     - Una foto que el lector no leyó —sin archivo, ilegible o que el modelo no devolvió— es de
       todos los productos. Antes salía como un borrador basura.
     - Del pie con dos bloques vale el de la fecha más reciente, y lo decide el dominio: el lector
       devuelve todos los bloques. Cuando se lo pedíamos al modelo, tomó el SKU viejo en las trece
       fotos de un álbum.
     - Estos productos no cuentan para el tope de cinco del 2 de octubre, que es de productos
       escritos en el texto.
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
