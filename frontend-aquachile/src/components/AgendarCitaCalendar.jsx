import { useEffect, useState } from 'react';
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
  fechaCitaInicial,
  horaCitaInicial,
}) {
  const horaNormalizada = horaCitaInicial ? String(horaCitaInicial).slice(0, 5) : '';

  const [fecha, setFecha] = useState(fechaCitaInicial || fechaDiaHabilDefault);
  const [horaInicio, setHoraInicio] = useState(
    horaNormalizada || BLOQUES_HORARIOS_40_MIN[0].valor
  );

  useEffect(() => {
    if (fechaCitaInicial) setFecha(fechaCitaInicial);
  }, [fechaCitaInicial]);

  useEffect(() => {
    if (horaCitaInicial) {
      setHoraInicio(String(horaCitaInicial).slice(0, 5));
    }
  }, [horaCitaInicial]);

  const urlCalendar = construirUrlGoogleCalendar({
    nombreCandidato,
    correoCandidato,
    cargo,
    ubicacion,
    fecha,
    horaInicio,
  });

  return (
    <div className="mt-4 rounded-xl border border-teal-200 bg-teal-50/60 p-4">
      <div>
        <h3 className="text-sm font-semibold text-teal-950">
          Apartar cita en Google Calendar
        </h3>
        <p className="text-xs text-teal-800">
          Selecciona el día y el bloque horario (40 min) para agendar a{' '}
          <strong>{nombreCandidato}</strong> ({cargo || 'Postulante'}).
        </p>
      </div>

      <div className="mt-3 flex flex-wrap items-end gap-3">
        <div>
          <label className="block text-xs font-medium text-slate-700 mb-1">
            Fecha de la cita
          </label>
          <input
            type="date"
            value={fecha}
            min={new Date().toISOString().split('T')[0]}
            onChange={(e) => setFecha(e.target.value)}
            className="rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-sm text-slate-800"
          />
        </div>

        <div>
          <label className="block text-xs font-medium text-slate-700 mb-1">
            Horario disponible
          </label>
          <select
            value={horaInicio}
            onChange={(e) => setHoraInicio(e.target.value)}
            className="rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-sm text-slate-800"
          >
            {horaNormalizada &&
              !BLOQUES_HORARIOS_40_MIN.some((b) => b.valor === horaNormalizada) && (
                <option value={horaNormalizada}>{horaNormalizada} hrs</option>
              )}
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
          className="inline-flex items-center gap-2 rounded-lg bg-teal-700 px-4 py-2 text-sm font-medium text-white shadow-sm transition hover:bg-teal-800"
        >
          Apartar en Google Calendar
        </a>
      </div>
    </div>
  );
}