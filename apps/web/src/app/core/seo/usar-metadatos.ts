import { effect, inject } from '@angular/core';
import { MetadatosPagina } from './metadatos.model';
import { MetadatosSeo } from './metadatos.servicio';

/**
 * Metadatos que dependen de datos: los declara la pantalla, no su ruta.
 *
 * Se llama desde el inicializador de un campo o desde el constructor del
 * componente, y `fuente` devuelve `null` mientras no haya con qué armar el
 * título — la ficha, antes de que llegue el producto. Ese `null` no es un caso
 * de borde: es el estado en el que la ruta ya declaró un título genérico
 * decente, y pisarlo con uno vacío sería peor que dejarlo.
 *
 * Precedencia y idioma quedan resueltos por construcción. El componente se crea
 * después de que la navegación termina, así que su primera escritura llega
 * después de la de la ruta y gana; y como el efecto depende de las mismas
 * señales que el resto de la pantalla —incluida la del traductor—, un cambio de
 * idioma lo vuelve a ejecutar sin que nadie tenga que acordarse.
 */
export function usarMetadatos(fuente: () => MetadatosPagina | null): void {
  const metadatos = inject(MetadatosSeo);
  effect(() => {
    const pagina = fuente();
    if (pagina) {
      metadatos.aplicar(pagina);
    }
  });
}

