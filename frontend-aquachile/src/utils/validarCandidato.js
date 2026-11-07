// Validación compartida para registrar y editar candidatos.
// Las restricciones son reglas de interfaz del MVP académico, no requisitos legales.
export function normalizarCandidato(candidato) {
  return Object.fromEntries(
    Object.entries(candidato).map(([clave, valor]) => [
      clave, typeof valor === 'string' ? valor.trim() : valor,
    ])
  );
}

export function validarCandidato(candidato) {
  const datos = normalizarCandidato(candidato);
  const errores = {};

  if (!datos.nombre || datos.nombre.length < 3) {
    errores.nombre = 'Ingresa un nombre de al menos 3 caracteres.';
  }
  if (!datos.correo || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(datos.correo)) {
    errores.correo = 'Ingresa un correo electrónico válido.';
  }
  if (!datos.cargoPostula || datos.cargoPostula.length < 2) {
    errores.cargoPostula = 'Indica el cargo al que postula (mínimo 2 caracteres).';
  }
  if (datos.telefono) {
    const digitos = datos.telefono.replace(/\D/g, '');
    if (!/^\+?[\d\s()-]+$/.test(datos.telefono) || digitos.length < 8 || digitos.length > 15) {
      errores.telefono = 'Ingresa un teléfono válido (8 a 15 dígitos) o déjalo vacío.';
    }
  }
  if (!['Interno', 'Externo'].includes(datos.origen)) {
    errores.origen = 'Selecciona un origen válido.';
  }

  return { datos, errores, valido: Object.keys(errores).length === 0 };
}
