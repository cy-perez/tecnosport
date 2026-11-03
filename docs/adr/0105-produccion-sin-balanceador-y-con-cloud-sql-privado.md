# ADR-0105: producción sin balanceador y con Cloud SQL privado

Fecha: 2026-11-03
Estado: aceptada
Reemplaza: en `docs/07-infra-gcp.md` e `infra/README.md`, el balanceador de carga como entrada de
producción y el "Cloud Storage y CDN" para las imágenes.

## Contexto

Producción se describía como dev más un balanceador, Cloud SQL, `min-instances = 1` y Secret
Manager. El `min-instances = 1` cayó con ADR-0104. Quedaba el balanceador: unos US$18 al mes fijos
para un sitio que arranca con poco tráfico, y su único trabajo era darle dominio y TLS al sitio y
mandar `/api` a la API.

Eso último ya lo hace la web: `apps/web/src/proxy-api.ts` reenvía `/api` a la API desde el mismo
origen, y así funciona dev desde que la cookie de sesión exigió un solo dominio. Lo que falta es el
dominio y el certificado.

Firebase Hosting se descartó primero: delante de Cloud Run solo deja pasar la cookie
`__session`, y la de la sesión se llama de otra forma.

## Decisión

1. **El dominio se mapea a la web con el mapeo de dominios de Cloud Run.** `tecnosport.co`, `www`
   y `ensayo.tecnosport.co` apuntan a `tecnosport-web`, y Google emite y renueva el certificado.
   El de ensayo es un CNAME que no toca la raíz: se comprueba con él que el certificado sale y el
   sitio sirve antes de mover `tecnosport.co`.
2. **La API se publica en su URL determinista de `run.app`**, que sale del número del proyecto y
   se conoce antes de crear el servicio. De ella cuelgan los webhooks de Wompi, Skydropx y
   Sistecrédito, el tick y la ingesta, sin el segundo `apply` que dev necesita.
3. **Cloud SQL `db-f1-micro` sin IP pública.** La API llega a ella por Direct VPC egress, solo para
   los rangos privados; lo demás sale directo, sin NAT. TLS obligatorio también dentro de la red.
   Respaldo diario con 30 guardados y recuperación a cualquier instante de los últimos siete días.
   El grupo de conexiones de la API baja a 5, porque la instancia admite 25 y la API puede tener
   tres instancias.
4. **Las migraciones las corre un Cloud Run Job dentro de la red** (`apps/api/migraciones`), con
   una cuenta que solo lee la clave de la base. El runner de GitHub no llega a una base sin IP
   pública, y además así la cuenta de despliegue no conoce esa clave, que en dev sí conoce.
5. **Producción no construye: promueve.** `desplegar-prod.yml` corre a demanda con el SHA de un
   commit de `main` que ya corrió en dev, copia sus tres imágenes del registro de dev al de
   producción, migra, despliega y comprueba la salud, revirtiendo si falla. Copia y no lee del
   registro de dev porque allá se borra lo de más de siete días, y una revisión sin imagen no se
   restaura.
6. **El despliegue exige aprobación.** El trabajo declara el ambiente `produccion` de GitHub, y la
   federación de identidad de producción solo entrega credenciales a un token de `main` que traiga
   ese ambiente.
7. **Las imágenes de producto se sirven desde el bucket, sin CDN.**

Decisiones de negocio del mismo día que el Terraform fija: contraentrega apagada, recogida en el
punto encendida, la publicación en redes sale de producción y dev deja de publicar, la ingesta de
proveedores corre en producción, y `contacto@tecnosport.co` es a la vez el remitente de los
correos y la cuenta del panel.

## Consecuencias

- **El mapeo de dominios está en preview**, y Google dice de él que no está listo para producción.
  Si falla, la salida es el balanceador; la web, la API y el DNS no cambian, solo lo que hay en
  medio.
- **No se pueden apagar TLS 1.0 y 1.1.** Los navegadores actuales ya no los usan, así que el
  riesgo es bajo; cerrarlo también pide el balanceador.
- **El certificado de la raíz se emite después de mover el DNS**, entre 15 minutos y 24 horas.
  En ese rato `https://tecnosport.co` muestra un error de certificado. Se mueve de madrugada, con
  el TTL ya bajado, y el sitio se anuncia cuando el certificado está activo.
- **El HSTS de la web lleva `includeSubDomains`.** Un subdominio del negocio que solo sirva HTTP
  dejaría de abrirse en el navegador de quien visitó la tienda. Se revisa la zona antes de mover
  la raíz.
- **El arranque en frío casi no se ve**: el tick de cada minuto mantiene caliente una instancia la
  mayor parte del tiempo, y solo cobra la CPU de la petición.
- **Un SHA viejo no se promueve.** Si su imagen ya salió del registro de dev, se vuelve a desplegar
  en dev primero.
- **`IP_CLIENTE_SALTOS_CONFIABLES` sigue en 1**: sin balanceador no hay un salto más que contar.
- **Costo estimado:** US$12–20 al mes, casi todo de Cloud SQL. Un presupuesto con avisos al 50, al
  90 y al 100 % de US$30 lo vigila.
