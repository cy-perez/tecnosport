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
  type = string
}

variable "publico" {
  type    = bool
  default = true
}
