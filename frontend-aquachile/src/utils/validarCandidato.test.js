import { describe, expect, it } from 'vitest';
import { normalizarCandidato, validarCandidato } from './validarCandidato';

const candidatoValido = {
  nombre: 'María Torres', correo: 'maria@example.test', telefono: '+56 9 1234 5678',
  cargoPostula: 'Analista', familiaCargo: '', origen: 'Externo', ubicacion: '', unidad: '',
};

describe('Validaciones de candidatos', () => {
  it('acepta un candidato válido', () => {
    expect(validarCandidato(candidatoValido).valido).toBe(true);
  });
  it('rechaza datos obligatorios compuestos solo por espacios', () => {
    const resultado = validarCandidato({ ...candidatoValido, nombre: '    ', cargoPostula: ' ' });
    expect(resultado.valido).toBe(false);
    expect(resultado.errores.nombre).toBeTruthy();
    expect(resultado.errores.cargoPostula).toBeTruthy();
  });
  it('rechaza formatos incorrectos de correo y teléfono', () => {
    const resultado = validarCandidato({ ...candidatoValido, correo: 'correo-invalido', telefono: 'abc' });
    expect(resultado.errores.correo).toBeTruthy();
    expect(resultado.errores.telefono).toBeTruthy();
  });
  it('normaliza espacios antes de enviar', () => {
    const datos = normalizarCandidato({ ...candidatoValido, nombre: '  María Torres  ' });
    expect(datos.nombre).toBe('María Torres');
  });
});
