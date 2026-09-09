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
