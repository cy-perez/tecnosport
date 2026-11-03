variable "nombre" {
  description = "Nombre del servicio."
  type        = string
}

variable "region" {
  type = string
}

variable "imagen" {
  description = "Imagen inicial. La real la pone el despliegue; ver `ignore_changes` en main.tf."
  type        = string
}

variable "cuenta_de_servicio" {
  description = "Correo de la cuenta con la que corre el servicio. Una por servicio, no la de por omisión del proyecto, que es administradora del editor."
  type        = string
}

variable "variables" {
  description = "Variables de entorno con valor en claro. Nada secreto aquí: esto queda en el estado."
  type        = map(string)
  default     = {}
}

variable "secretos" {
  description = "Variables de entorno que salen de Secret Manager: nombre de la variable -> id del secreto."
  type        = map(string)
  default     = {}
}

variable "instancias_minimas" {
  type    = number
  default = 0
}

variable "instancias_maximas" {
  description = "Techo bajo a propósito: en dev, un bucle que dispare peticiones no debe poder gastar la capa gratuita de un mes en una tarde."
  type        = number
  default     = 3
}

variable "cpu" {
  type    = string
  default = "1"
}

variable "memoria" {
  type    = string
  default = "512Mi"
}

variable "ruta_de_salud" {
  description = "Ruta para la sonda de arranque HTTP. `null` —lo normal mientras el servicio corra la imagen de arranque— deja la espera por omisión de Cloud Run, que es que el proceso escuche en el puerto."
  type        = string
  default     = null
}

variable "publico" {
  type    = bool
  default = true
}

variable "tiempo_maximo_de_peticion" {
  description = "Cuánto espera Cloud Run una respuesta, como duración en segundos (`\"300s\"`). El valor por omisión es el de Cloud Run; la API lo sube para los tramos de la ingesta (ADR-0080)."
  type        = string
  default     = "300s"
}

variable "cpu_siempre_asignada" {
  description = "Si la instancia conserva CPU entre peticiones. `false` —lo de dev y lo de producción— es CPU solo mientras atiende, que es lo que deja escalar a cero; las tareas programadas no lo necesitan porque las dispara el tick de Cloud Scheduler dentro de una petición (ADR-0104)."
  type        = bool
  default     = false
}

variable "red_privada" {
  description = "La subred por la que el servicio sale a rangos privados, con Direct VPC egress: es como la API de producción llega a Cloud SQL, que no tiene IP pública. `null` —lo de dev, que habla con Neon por internet— no le pone red."
  type = object({
    red    = string
    subred = string
  })
  default = null
}
