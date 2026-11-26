/**
 * Formatea un objeto Date al estándar UTC exigido por Google Calendar (YYYYMMDDTHHMMSSZ)
 */
function formatearFechaGoogle(date) {
  return date.toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '');
}

/**
 * Genera bloques horarios de 40 minutos entre las 10:00 AM y las 7:00 PM (19:00).
 */
export const BLOQUES_HORARIOS_40_MIN = (() => {
  const bloques = [];
  const inicioJornadaMin = 10 * 60; // 10:00 (600 min)
  const finJornadaMin = 19 * 60;    // 19:00 (1140 min)
  const duracion = 40;

  for (let min = inicioJornadaMin; min + duracion <= finJornadaMin; min += duracion) {
    const hIni = String(Math.floor(min / 60)).padStart(2, '0');
    const mIni = String(min % 60).padStart(2, '0');
    const fin = min + duracion;
    const hFin = String(Math.floor(fin / 60)).padStart(2, '0');
    const mFin = String(fin % 60).padStart(2, '0');

    bloques.push({
      valor: `${hIni}:${mIni}`,
      etiqueta: `${hIni}:${mIni} - ${hFin}:${mFin} hrs`,
    });
  }
  return bloques;
})();

/**
 * Devuelve la fecha sugerida por defecto en formato YYYY-MM-DD (próximo día hábil).
 */
export function fechaDiaHabilDefault() {
  const fecha = new Date();
  fecha.setDate(fecha.getDate() + 1);
  if (fecha.getDay() === 6) fecha.setDate(fecha.getDate() + 2); // sábado -> lunes
  if (fecha.getDay() === 0) fecha.setDate(fecha.getDate() + 1); // domingo -> lunes

  const anio = fecha.getFullYear();
  const mes = String(fecha.getMonth() + 1).padStart(2, '0');
  const dia = String(fecha.getDate()).padStart(2, '0');
  return `${anio}-${mes}-${dia}`;
}

/**
 * Construye la URL oficial de Google Calendar con los datos de la cita prellenados (40 min fijos).
 */
export function construirUrlGoogleCalendar({
  nombreCandidato,
  correoCandidato,
  cargo,
  ubicacion,
  fecha,       // string 'YYYY-MM-DD'
  horaInicio,  // string 'HH:mm' (ej. '10:00')
}) {
  const [anio, mes, dia] = (fecha || fechaDiaHabilDefault()).split('-').map(Number);
  const [hora, minuto] = (horaInicio || '10:00').split(':').map(Number);

  const inicio = new Date(anio, mes - 1, dia, hora, minuto, 0);
  const fin = new Date(inicio.getTime() + 40 * 60 * 1000); // 40 minutos exactos

  const titulo = `Entrevista Psicolaboral - ${cargo || 'Postulación'} (${nombreCandidato})`;
  const detalles = [
    `Entrevista de evaluación psicolaboral · AquaChile`,
    `Postulante: ${nombreCandidato}`,
    correoCandidato ? `Correo: ${correoCandidato}` : null,
    cargo ? `Cargo: ${cargo}` : null,
    ubicacion ? `Ubicación / Modalidad: ${ubicacion}` : null,
    `Duración estimada: 40 minutos`,
  ]
    .filter(Boolean)
    .join('\n');

  const params = new URLSearchParams({
    action: 'TEMPLATE',
    text: titulo,
    dates: `${formatearFechaGoogle(inicio)}/${formatearFechaGoogle(fin)}`,
    details: detalles,
    location: ubicacion || 'Videollamada / Plataforma AquaChile',
  });

  if (correoCandidato) {
    params.append('add', correoCandidato);
  }

  return `https://calendar.google.com/calendar/render?${params.toString()}`;
}