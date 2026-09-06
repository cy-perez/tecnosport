import { urlPreferida } from './producto.model';

describe('urlPreferida', () => {
  it('prefiere la WebP', () => {
    expect(urlPreferida({ url: 'foto.jpg', urlWebp: 'foto.webp' })).toBe('foto.webp');
  });

  // El backend puede no haber generado la WebP de una imagen vieja. Servirla en el formato
  // anterior es peor que servirla en WebP, y mucho mejor que no servirla.
  it('cae al original cuando no hay WebP', () => {
    expect(urlPreferida({ url: 'foto.jpg', urlWebp: '' })).toBe('foto.jpg');
  });
});
