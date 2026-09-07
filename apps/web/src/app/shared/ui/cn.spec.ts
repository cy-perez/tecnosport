import { cn } from './cn';

describe('cn', () => {
  it('la última clase gana cuando dos tocan la misma propiedad', () => {
    expect(cn('p-16', 'p-24')).toBe('p-24');
  });

  it('conserva clases que no compiten entre sí', () => {
    const resultado = cn('p-16', 'bg-ts-primario', 'font-display');
    expect(resultado).toContain('p-16');
    expect(resultado).toContain('bg-ts-primario');
    expect(resultado).toContain('font-display');
  });

  // El caso que motivó el helper: la base del componente contra la clase de
  // quien lo usa. Con los colores propios del proyecto, no los de Tailwind.
  it('resuelve los colores de marca como un solo grupo', () => {
    expect(cn('bg-ts-primario', 'bg-ts-acento')).toBe('bg-ts-acento');
    expect(cn('text-ts-texto', 'text-ts-texto-suave')).toBe('text-ts-texto-suave');
  });

  it('no confunde el color del texto con su tamaño', () => {
    const resultado = cn('text-ts-texto', 'text-2xl');
    expect(resultado).toContain('text-ts-texto');
    expect(resultado).toContain('text-2xl');
  });

  // Los variants del proyecto son propios (`oscuro:`, `movil:`), no los de
  // Tailwind: si `twMerge` los tratara como parte del nombre de la clase,
  // fusionaría la versión clara con la oscura y el tema se rompería.
  it('trata un variant propio como un ámbito aparte', () => {
    const resultado = cn('bg-ts-superficie', 'oscuro:bg-ts-superficie-alt');
    expect(resultado).toContain('bg-ts-superficie');
    expect(resultado).toContain('oscuro:bg-ts-superficie-alt');
  });

  it('sí fusiona dos clases con el mismo variant propio', () => {
    expect(cn('oscuro:bg-ts-superficie', 'oscuro:bg-ts-fondo')).toBe('oscuro:bg-ts-fondo');
  });

  it('acepta condicionales y descarta lo falso', () => {
    const oculto = false;

    expect(cn('p-16', oculto && 'hidden', undefined, ['font-medio'])).toBe('p-16 font-medio');
  });
});

// Regresión del vocabulario propio. Estas pruebas existen porque el bug real
// no lo vio ninguna prueba de componente: `font-texto` estaba en el
// componente y desaparecía al fusionar, así que el botón se pintaba en Arial.
describe('cn con los nombres de token del proyecto', () => {
  it('no confunde la familia tipográfica con el peso', () => {
    const resultado = cn('font-texto', 'font-medio');

    expect(resultado).toContain('font-texto');
    expect(resultado).toContain('font-medio');
  });

  it('dos pesos sí compiten entre sí', () => {
    expect(cn('font-medio', 'font-fuerte')).toBe('font-fuerte');
  });

  it('dos familias sí compiten entre sí', () => {
    expect(cn('font-texto', 'font-display')).toBe('font-display');
  });

  it('dos interlineados compiten entre sí', () => {
    expect(cn('leading-titulares', 'leading-texto')).toBe('leading-texto');
  });

  it('dos anchos de contenedor compiten entre sí', () => {
    expect(cn('max-w-contenido', 'max-w-filtro')).toBe('max-w-filtro');
    expect(cn('max-w-formulario', 'max-w-formulario-lg')).toBe('max-w-formulario-lg');
  });

  it('el mínimo táctil compite con otro min-height', () => {
    expect(cn('min-h-tactil', 'min-h-0')).toBe('min-h-0');
  });

  // Lo que ya funcionaba sin configurar, fijado para que la configuración
  // nueva no lo rompa.
  it('el color del borde y su ancho son cosas distintas', () => {
    const resultado = cn('border-ts-borde', 'border-0');

    expect(resultado).toContain('border-ts-borde');
    expect(resultado).toContain('border-0');
  });

  it('el tamaño de fuente sigue compitiendo por separado del color', () => {
    expect(cn('text-xs', 'text-4xl')).toBe('text-4xl');
  });
});
