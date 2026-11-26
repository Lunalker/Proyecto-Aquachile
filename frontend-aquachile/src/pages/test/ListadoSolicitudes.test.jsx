import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import ListadoSolicitudes from '../ListadoSolicitudes';
import api from '../../services/api';
import * as AuthContext from '../../context/AuthContext';
import { descargarExcel } from '../../utils/exportarExcel';

vi.mock('../../services/api', () => ({
  default: { get: vi.fn() },
}));

vi.mock('../../components/EstadoBadge', () => ({
  default: ({ estado }) => <span>{estado}</span>,
}));

vi.mock('../../utils/exportarExcel', () => ({
  descargarExcel: vi.fn(),
}));

const solicitudesMock = [
  {
    id: 1,
    candidato: { id: 101, nombre: 'Carlos Ruiz', correo: 'carlos@ejemplo.cl', cargoPostula: 'Supervisor', origen: 'Externo' },
    cargo: 'Supervisor',
    familiaCargo: 'Mantenimiento',
    profesionalResponsable: 'Ana Evaluadora',
    fechaSolicitud: '2026-03-01',
    estado: 'Pendiente',
    observaciones: 'Obs 1',
  },
  {
    id: 2,
    candidato: { id: 102, nombre: 'Laura Soto', correo: 'laura@ejemplo.cl', cargoPostula: 'Veterinaria', origen: 'Interno' },
    cargo: 'Veterinaria',
    familiaCargo: 'Salud',
    profesionalResponsable: 'Pedro Evaluador',
    fechaSolicitud: '2026-03-02',
    estado: 'Finalizada',
    observaciones: 'Obs 2',
  },
];

const evaluacionesMock = [
  {
    id: 50,
    solicitud: { id: 2 },
    estadoEvaluacion: 'Recomendado',
    resultado: 'Aprobada sin observaciones',
    fechaEvaluacion: '2026-03-05',
  },
];

describe('Vista ListadoSolicitudes', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(AuthContext, 'useAuth').mockReturnValue({ usuario: { rol: 'EVALUADOR' } });
    api.get.mockImplementation((url) => {
      if (url.includes('/solicitudes')) return Promise.resolve({ data: solicitudesMock });
      if (url.includes('/evaluaciones')) return Promise.resolve({ data: evaluacionesMock });
      return Promise.reject(new Error('Ruta no encontrada'));
    });
  });

  it('debe listar las solicitudes y permitir filtrar por búsqueda, fecha y limpiar filtros', async () => {
    render(<MemoryRouter><ListadoSolicitudes /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByText('Carlos Ruiz')).toBeInTheDocument();
      expect(screen.getByText('Laura Soto')).toBeInTheDocument();
    });

    const inputBuscar = screen.getByRole('searchbox', { name: /buscar solicitud/i });
    fireEvent.change(inputBuscar, { target: { value: 'Carlos' } });
    expect(screen.getByText('Carlos Ruiz')).toBeInTheDocument();
    expect(screen.queryByText('Laura Soto')).not.toBeInTheDocument();

    // Limpiar filtros
    const btnLimpiar = screen.getByRole('button', { name: /limpiar filtros/i });
    expect(btnLimpiar).not.toBeDisabled();
    fireEvent.click(btnLimpiar);
    expect(screen.getByText('Laura Soto')).toBeInTheDocument();

    // Filtrar por fecha
    const inputFecha = screen.getByLabelText(/fecha de solicitud/i);
    fireEvent.change(inputFecha, { target: { value: '2026-03-02' } });
    expect(screen.queryByText('Carlos Ruiz')).not.toBeInTheDocument();
    expect(screen.getByText('Laura Soto')).toBeInTheDocument();
  });

  it('debe filtrar por estado utilizando el select', async () => {
    render(<MemoryRouter><ListadoSolicitudes /></MemoryRouter>);

    await waitFor(() => expect(screen.getByText('Carlos Ruiz')).toBeInTheDocument());

    const selectEstado = screen.getByRole('combobox', { name: /^Estado$/i });
    fireEvent.change(selectEstado, { target: { value: 'Finalizada' } });

    expect(screen.queryByText('Carlos Ruiz')).not.toBeInTheDocument();
    expect(screen.getByText('Laura Soto')).toBeInTheDocument();
  });

  it('debe mostrar botones Gestionar y Ver CV para rol EVALUADOR', async () => {
    render(<MemoryRouter><ListadoSolicitudes /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getAllByText('Gestionar').length).toBe(2);
      const linksCv = screen.getAllByRole('link', { name: /ver cv/i });
      expect(linksCv[0]).toHaveAttribute('href', '/documentos/101');
      expect(linksCv[1]).toHaveAttribute('href', '/documentos/102');
    });
  });

  it('debe mostrar vista de solo consulta y acceso a candidatos para rol ANALISTA', async () => {
    vi.spyOn(AuthContext, 'useAuth').mockReturnValue({ usuario: { rol: 'ANALISTA' } });
    render(<MemoryRouter><ListadoSolicitudes /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /solicitudes psicolaborales/i })).toBeInTheDocument();
      expect(screen.getAllByText('Solo consulta').length).toBe(2);
      expect(screen.queryByText('Gestionar')).not.toBeInTheDocument();
      expect(screen.getByRole('link', { name: /ver candidatos/i })).toHaveAttribute('href', '/');
    });
  });

  it('debe exportar los datos combinados de candidatos, solicitudes y evaluaciones a Excel', async () => {
    render(<MemoryRouter><ListadoSolicitudes /></MemoryRouter>);

    await waitFor(() => expect(screen.getByText('Laura Soto')).toBeInTheDocument());

    const btnExcel = screen.getByRole('button', { name: /descargar excel/i });
    fireEvent.click(btnExcel);

    expect(descargarExcel).toHaveBeenCalledTimes(1);
    const argumento = descargarExcel.mock.calls[0][0];
    expect(argumento.nombreArchivo).toMatch(/^candidatos_solicitudes_\d{4}-\d{2}-\d{2}\.xlsx$/);
    expect(argumento.filas).toHaveLength(2);
    expect(argumento.filas[1]).toEqual(
      expect.arrayContaining(['Laura Soto', 'Veterinaria', 'Finalizada', 'Recomendado', 'Aprobada sin observaciones'])
    );
  });
});
