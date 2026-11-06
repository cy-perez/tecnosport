import { destinoSinWww } from './host-canonico';

const ORIGEN = 'https://tecnosport.co';

describe('destinoSinWww', () => {
  it('manda www al dominio sin www, con la misma ruta', () => {
    expect(destinoSinWww('www.tecnosport.co', '/es', ORIGEN)).toBe('https://tecnosport.co/es');
  });

  it('conserva la consulta', () => {
    expect(
      destinoSinWww('www.tecnosport.co', '/es/productos?linea=calzado&orden=precio', ORIGEN),
    ).toBe('https://tecnosport.co/es/productos?linea=calzado&orden=precio');
  });

  it('la raíz va a la raíz, y de ahí el servidor elige el idioma', () => {
    expect(destinoSinWww('www.tecnosport.co', '/', ORIGEN)).toBe('https://tecnosport.co/');
  });

  it('no distingue mayúsculas en el host', () => {
    expect(destinoSinWww('WWW.TecnoSport.co', '/es', ORIGEN)).toBe('https://tecnosport.co/es');
  });

  it('el dominio canónico sigue de largo', () => {
    expect(destinoSinWww('tecnosport.co', '/es', ORIGEN)).toBeNull();
  });

  // El caso que una regla "todo lo que no sea el canónico" rompería: el despliegue y las sondas
  // entran por la URL de Cloud Run.
  it('cualquier otro host sigue de largo', () => {
    expect(destinoSinWww('tecnosport-web-123.us-central1.run.app', '/es', ORIGEN)).toBeNull();
    expect(destinoSinWww('localhost', '/es', ORIGEN)).toBeNull();
    expect(destinoSinWww('www.otro-sitio.co', '/es', ORIGEN)).toBeNull();
  });

  it('sin host no redirige', () => {
    expect(destinoSinWww(undefined, '/es', ORIGEN)).toBeNull();
  });

  // En local el origen es localhost: nunca hay un www que redirigir.
  it('con el origen local no redirige nada', () => {
    expect(destinoSinWww('www.tecnosport.co', '/es', 'http://localhost:4200')).toBeNull();
  });

  // Una ruta que empieza con `//` no puede volverse otro host: `new URL('//malo.com', origen)`
  // la leería como una URL sin esquema, y esto sería una redirección abierta.
  it('una ruta con doble barra no se vuelve otro dominio', () => {
    expect(new URL(destinoSinWww('www.tecnosport.co', '//malo.com/x', ORIGEN)!).hostname).toBe(
      'tecnosport.co',
    );
  });
});
