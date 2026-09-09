# Valores no secretos del ambiente de desarrollo. Se versiona a propósito: es la diferencia entre
# "el ambiente está descrito" y "el ambiente lo sabe reconstruir quien lo montó".

# La URL que Cloud Run le asignó al servicio web. No se puede saber antes de crearlo, así que el
# primer apply va con esto vacío y el segundo la fija: los canónicos, el hreflang y el sitemap
# necesitan el nombre público del sitio, y sin él salen apuntando a localhost.
dominio_publico_web = "https://tecnosport-web-sdlqfchkiq-ue.a.run.app"

# Base de datos en Neon, región us-east-1 de AWS — la más cercana a us-east1 de Cloud Run.
#
# **El punto de conexión directo, no el del pooler.** Neon ofrece los dos: el host con `-pooler`
# pasa por PgBouncer, y ahí las migraciones de Flyway pueden encontrarse con sentencias que el
# pooler no admite en modo transacción. Con un solo host para todo, el directo es el correcto.
db_host    = "ep-withered-river-aubmpufh.c-10.us-east-1.aws.neon.tech"
db_nombre  = "tecnosport"
db_usuario = "tecnosport"

# La cadena que da Neon trae además `channel_binding=require`, que es un parámetro de libpq y no
# del driver de JDBC: no va en DB_PARAMS. Queda `?sslmode=require`, que es el valor por omisión de
# la variable, y es lo que Neon exige de verdad.

# Los siete secretos ya tienen al menos una versión (verificado el 9 de septiembre de 2026), así
# que los servicios pueden montarlos. Con esto en false, un secreto vacío haría que la revisión no
# arranque y Cloud Run lo reportaría como un error interno que no menciona los secretos.
secretos_cargados = true

# Llave pública de Wompi sandbox. No es secreta: el navegador la recibe en la respuesta del intento
# de pago para abrir el checkout, y además es con esta —no con la privada— con la que el backend
# consulta el estado de una transacción.
wompi_llave_publica = "pub_test_TXyoe5ZvJWZKfaCe2riF0NrCFIMvt96R"
