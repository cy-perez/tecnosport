# ADR-0107: el pie de las publicaciones anuncia todos los medios de pago, la otra red y el sitio

Fecha: 2026-11-05
Estado: aceptada
Reemplaza: el punto 1 de `adr/0102` (la plantilla de seis bloques) y su punto 2 en lo que toca a
la contraentrega y al envío.
Relacionados: `adr/0102`, `adr/0095` (precio publicado y de contado), `adr/0048` (Sistecrédito),
`adr/0094` (recogida en el punto).

## Contexto

El pie que proponía `ArmadorDePieDeFoto` desde el 30 de octubre decía «Paga en línea» y solo
anunciaba la contraentrega o el envío cuando el checkout los iba a ofrecer para ese producto. El
negocio pidió otra plantilla: todos los medios de pago con su emoji, la recogida en el punto, el
WhatsApp, los enlaces a las redes y al sitio, y un emoji por tipo de producto en la entrada.

Dos cosas de la petición no se podían hacer tal cual, y se dijeron antes de escribir código:

- **Ni Facebook ni Instagram admiten un enlace con texto** («Facebook» que lleve a la página).
  Solo URLs escritas enteras, y en Instagram ni esas se pueden tocar.
- **Addi no está en el checkout** desde la V61 (22 de septiembre de 2026). Anunciarlo hoy sería
  anunciar un medio de pago que quien entra por el enlace no encuentra.

## Decisión

1. **La plantilla**, por bloques separados por una línea en blanco: la entrada
   (`emoji nombre · precio`); la descripción, con el primer párrafo, una línea por talla (`📏`), por
   color (`🎨`) y por otro atributo (`▪️`), en ese orden, y hasta cuatro especificaciones (`✔️`)
   sacadas de las viñetas de la descripción —si no trae, no se pinta nada—; los pagos; el llamado;
   el WhatsApp; la otra red; el sitio, y los hashtags.
2. **Los pagos**: `💰 Diferentes opciones de pago:`, tarjeta débito o crédito y PSE, pago
   contraentrega, transferencia, los créditos, la recogida en el punto físico y `🚚 Envíos a toda
   Colombia`.
3. **La contraentrega, la recogida y el envío van en todos los productos**, por decisión del
   negocio: lo que el checkout no ofrezca para un producto en concreto se coordina por WhatsApp.
4. **Los créditos solo cuando aplican.** Sistecrédito si la pasarela está habilitada y la variante
   más cara que se vende alcanza el mismo mínimo que aplica el checkout
   (`tecnosport.sistecredito.monto-minimo`). Addi, igual, contra su propio mínimo, y **apagado por
   configuración** (`META_ANUNCIAR_ADDI=false`) hasta que llegue al checkout; su mínimo
   (`META_ADDI_MONTO_MINIMO`) es un dato de negocio que todavía no tenemos, y encenderlo sin él no
   arranca. Con los dos, una sola línea: `🏦 Addi o Sistecrédito.`
5. **Los enlaces según la red.** En Facebook el llamado lleva la URL de la ficha y el WhatsApp la
   de `wa.me`. En Instagram, donde no se pueden tocar, el llamado remite a la bio y el WhatsApp va
   como número. Cada red remite a la otra —en un post de Facebook, el enlace a Facebook no lleva a
   nada nuevo— y las dos al sitio, con su URL entera aunque en Instagram no se toque.
6. **El punto al final de cada renglón**, también después de `¿…?` como pidió el negocio, salvo
   tres: la entrada, porque `$289.900.` se lee como separador de miles; los que acaban en URL,
   porque Facebook puede tragarse el punto dentro del enlace; y los hashtags. El de la descripción y
   sus viñetas lo pone el armador cuando falta.
7. **El emoji es de la categoría**, editable desde el panel como sus hashtags: el suyo, el de su
   categoría padre o, si ninguna tiene, `📦`. Vive en la fila (`V155`) y no en un mapa por slug en
   los textos, que se rompería en silencio al renombrar una categoría.
8. **Los hashtags**: los de la categoría, el de la marca **solo en tecnología** y los fijos de
   `META_HASHTAGS_DE_MARCA`, que por omisión es `#tecnosport`.
9. **Los enlaces de WhatsApp y de las redes son textos** (`url.*` en `difusion_*.properties`), no
   propiedades de la aplicación: son los mismos del pie del sitio y de los correos, y
   `npm run datos-negocio` los compara contra esos.
10. **El título es `producto.nombre()`**, el mismo que pinta el `<h1>` de la ficha.

## Riesgos asumidos

- **La contraentrega y el envío se anuncian donde el checkout no los ofrece**: las líneas excluidas,
  los productos por encima del tope del recaudo, los que no están medidos y las ciudades sin
  cobertura (`ENVIO_SIN_COBERTURA`). `docs/04-ui-marca.md` ya había quitado «Envío a todo Colombia»
  del hero por esa razón. Lo anunciado obliga (Ley 1480) y el negocio lo asume, con WhatsApp para
  resolver lo que el checkout no resuelva. Si eso cambia, la condición que había —
  `PoliticaContraentrega.podriaAdmitirse` y las variantes medidas— está en la historia de
  `ArmadorDePieDeFoto`.
- **Las URL del bloque de redes en Instagram no se pueden tocar.** Se leen, y el negocio las quiere
  igual.

## Cómo se verificó

Pruebas del armador con el doble que pinta llaves: el orden de los bloques, los créditos contra
su mínimo (el exacto alcanza, se mide la variante más cara, una agotada no cuenta), el emoji propio,
el de la rama y el genérico, la marca fuera de tecnología, cada red remitiendo a la otra. Con los
textos de verdad, el pie entero de Facebook y de Instagram en castellano, que ninguna URL lleve
punto y que todo otro renglón lo lleve. No se publicó nada en Meta.
