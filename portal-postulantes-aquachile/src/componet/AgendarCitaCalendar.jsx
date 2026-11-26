import { useState } from 'react';
import {
  BLOQUES_HORARIOS_40_MIN,
  construirUrlGoogleCalendar,
  fechaDiaHabilDefault,
} from '../utils/generarEnlaceGoogleCalendar';

export default function AgendarCitaCalendar({
  nombreCandidato,
  correoCandidato,
  cargo,
  ubicacion,
}) {
  const [fecha, setFecha] = useState(fechaDiaHabilDefault);
  const [horaInicio, setHoraInicio] = useState(BLOQUES_HORARIOS_40_MIN[0].valor);

  const nombreMostrado = nombreCandidato?.trim() || 'Postulante';

  const urlCalendar = construirUrlGoogleCalendar({
    nombreCandidato: nombreMostrado,
    correoCandidato,
    cargo,
    ubicacion,
    fecha,
    horaInicio,
  });

  return (
    <div
      style={{
        padding: '1rem',
        borderRadius: '10px',
        background: '#f0fdfa',
        border: '1px solid #99f6e4',
        display: 'flex',
        flexDirection: 'column',
        gap: '0.75rem',
      }}
    >
      <div>
        <strong style={{ display: 'block', fontSize: '0.92rem', color: '#134e4a' }}>
          Apartar cita de entrevista en Google Calendar
        </strong>
        <span style={{ fontSize: '0.82rem', color: '#115e59' }}>
          Selecciona el día y el bloque horario (40 min) para agendar la entrevista de{' '}
          <strong>{nombreMostrado}</strong> ({cargo || 'Vacante seleccionada'}).
        </span>
      </div>

      <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'flex-end', gap: '0.65rem' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
          <label
            htmlFor="cita-fecha"
            style={{ fontSize: '0.8rem', fontWeight: 600, color: '#0f172a' }}
          >
            Fecha de la cita
          </label>
          <input
            id="cita-fecha"
            type="date"
            value={fecha}
            min={new Date().toISOString().split('T')[0]}
            onChange={(e) => setFecha(e.target.value)}
            style={{
              padding: '0.5rem 0.7rem',
              borderRadius: '8px',
              border: '1px solid #cbd5e1',
              background: '#ffffff',
              fontSize: '0.88rem',
              color: '#0f172a',
            }}
          />
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
          <label
            htmlFor="cita-bloque"
            style={{ fontSize: '0.8rem', fontWeight: 600, color: '#0f172a' }}
          >
            Horario disponible
          </label>
          <select
            id="cita-bloque"
            value={horaInicio}
            onChange={(e) => setHoraInicio(e.target.value)}
            style={{
              padding: '0.5rem 0.7rem',
              borderRadius: '8px',
              border: '1px solid #cbd5e1',
              background: '#ffffff',
              fontSize: '0.88rem',
              color: '#0f172a',
            }}
          >
            {BLOQUES_HORARIOS_40_MIN.map((bloque) => (
              <option key={bloque.valor} value={bloque.valor}>
                {bloque.etiqueta}
              </option>
            ))}
          </select>
        </div>

        <a
          href={urlCalendar}
          target="_blank"
          rel="noopener noreferrer"
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '0.55rem 0.95rem',
            borderRadius: '8px',
            background: '#0f766e',
            color: '#ffffff',
            fontWeight: 600,
            fontSize: '0.88rem',
            textDecoration: 'none',
          }}
        >
          Apartar en Google Calendar
        </a>
      </div>
    </div>
  );
}