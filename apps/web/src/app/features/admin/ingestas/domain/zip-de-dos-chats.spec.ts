import { esLaEstructuraAcordada, estructuraDeDosChats } from './zip-de-dos-chats';

describe('el zip de dos chats', () => {
  const meraki = estructuraDeDosChats('Meraki');

  it('sale del nombre del proveedor', () => {
    expect(meraki).toEqual({
      zip: 'Meraki.zip',
      general: 'Meraki.txt',
      caballero: 'MerakiMen.txt',
    });
  });

  it('acepta los dos .txt acordados con las fotos de los dos chats, en cualquier orden', () => {
    expect(
      esLaEstructuraAcordada(meraki, 'Meraki.zip', [
        'IMG-20261007-WA0040.jpg',
        'MerakiMen.txt',
        '00000125-VIDEO-2026-10-06-09-14-29.mp4',
        'Meraki.txt',
      ]),
    ).toBe(true);
  });

  /** Una carpeta dentro del zip no cambia nada: el servidor lee los archivos por su nombre. */
  it('ignora las carpetas', () => {
    expect(
      esLaEstructuraAcordada(meraki, 'Meraki.zip', [
        'Meraki/',
        'Meraki/Meraki.txt',
        'Meraki/MerakiMen.txt',
      ]),
    ).toBe(true);
  });

  it('rechaza todo lo que no es exactamente eso', () => {
    const acordados = ['Meraki.txt', 'MerakiMen.txt'];
    expect(esLaEstructuraAcordada(meraki, 'meraki.zip', acordados)).toBe(false);
    expect(esLaEstructuraAcordada(meraki, 'Meraki (1).zip', acordados)).toBe(false);
    expect(esLaEstructuraAcordada(meraki, 'Meraki.zip', ['Meraki.txt'])).toBe(false);
    expect(esLaEstructuraAcordada(meraki, 'Meraki.zip', ['_chat.txt', 'MerakiMen.txt'])).toBe(
      false,
    );
    expect(esLaEstructuraAcordada(meraki, 'Meraki.zip', [...acordados, 'otro.txt'])).toBe(false);
    expect(esLaEstructuraAcordada(meraki, 'Meraki.zip', ['Meraki.txt', 'merakimen.txt'])).toBe(
      false,
    );
  });
});
