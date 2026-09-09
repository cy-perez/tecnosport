# Valores no secretos del ambiente de desarrollo. Se versiona a propósito: es la diferencia entre
# "el ambiente está descrito" y "el ambiente lo sabe reconstruir quien lo montó".

# La URL que Cloud Run le asignó al servicio web. No se puede saber antes de crearlo, así que el
# primer apply va con esto vacío y el segundo la fija: los canónicos, el hreflang y el sitemap
# necesitan el nombre público del sitio, y sin él salen apuntando a localhost.
dominio_publico_web = "https://tecnosport-web-sdlqfchkiq-ue.a.run.app"

# Base de datos en Neon. Vacío hasta que exista el proyecto allá; mientras lo esté, el servicio de
# la API no recibe ninguna variable de base de datos en vez de recibirlas a medias.
db_host    = ""
db_nombre  = ""
db_usuario = ""
