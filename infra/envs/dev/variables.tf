variable "proyecto" {
  description = "Proyecto de GCP del ambiente de desarrollo. Nunca el de producción."
  type        = string
  default     = "tecnosport-dev"
}

variable "region" {
  description = "Una sola región para todo el ambiente. us-east1 porque ahí está el bucket de imágenes que ya existía, y es una de las tres que admite la capa gratuita de Storage."
  type        = string
  default     = "us-east1"
}

variable "repositorio_github" {
  description = "El repositorio que puede desplegar, en formato duenio/nombre. La federación de identidad se acota a él: sin esa condición, cualquier repositorio de GitHub podría pedir un token de esta cuenta."
  type        = string
  default     = "cy-perez/tecnosport"
}

variable "rama_que_despliega" {
  description = "Solo esta referencia puede desplegar. Un pull request de un desconocido no toca la infraestructura."
  type        = string
  default     = "refs/heads/main"
}

variable "bucket_imagenes" {
  description = "Bucket de imágenes de producto que ya existe, creado por infra/dev/bucket-imagenes.mjs. Aquí solo se le dan permisos a la API."
  type        = string
  default     = "tecnosport-dev-imagenes"
}

variable "dominio_publico_web" {
  description = "Origen público del sitio. Se llena después del primer apply, cuando Cloud Run asigna la URL, y se vuelve a aplicar: APP_URL_PUBLICA y NG_ALLOWED_HOSTS lo necesitan."
  type        = string
  default     = ""
}

variable "secretos_cargados" {
  description = "Si los secretos de Secret Manager ya tienen al menos una versión. Mientras sea false, los servicios no los montan: un secreto vacío hace que la revisión no arranque, y Cloud Run lo reporta como un error interno que no menciona los secretos."
  type        = bool
  default     = false
}

variable "db_host" {
  description = "Punto de conexión de Neon. Vacío mientras no exista: con esto vacío, el servicio no recibe ninguna variable de base de datos, que es mejor que recibirlas a medias."
  type        = string
  default     = ""
}

variable "db_nombre" {
  type    = string
  default = ""
}

variable "db_usuario" {
  type    = string
  default = ""
}

variable "db_params" {
  description = "Parámetros de la URL de JDBC. Neon **exige** TLS, y sin esto la conexión se rechaza; la plantilla de application.yml no tenía dónde ponerlos."
  type        = string
  default     = "?sslmode=require"
}

variable "wompi_llave_publica" {
  description = "Llave pública de Wompi sandbox (`pub_test_...`). No es secreta —el navegador la recibe para abrir el checkout— así que va aquí y no en Secret Manager. Vacía mientras no se tenga: la aplicación arranca igual, con un valor de relleno que Wompi rechaza, y el pago con tarjeta no funciona."
  type        = string
  default     = ""
}
