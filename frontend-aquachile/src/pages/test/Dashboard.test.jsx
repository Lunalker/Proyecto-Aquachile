import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import Dashboard from '../Dashboard';
import api from '../../services/api';

vi.mock('../../services/api', () => ({
  default: { get: vi.fn() },
}));

const candidatosMock = [
  { id: 1, nombre: 'Ana López', correo: 'ana@aquachile.cl', cargoPostula: 'Jefa de Turno', familiaCargo: 'Operaciones', origen: 'Interno' },
  { id: 2, nombre: 'Carlos Vera', correo: 'carlos@aquachile.cl', cargoPostula: 'Analista BI', familiaCargo: 'TI', origen: 'Externo' },
];

const solicitudesMock = [
  { id: 10, estado: 'Pendiente' },
  { id: 11, estado: 'En proceso' },
  { id: 12, estado: 'Finalizada' },
  { id: 13, estado: 'Finalizada' },
];

describe('Pruebas integradas - Dashboard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('debe mostrar los indicadores correctos, barras de progreso y el listado de candidatos', async () => {
    api.get.mockImplementation((url) => {
      if (url.includes('/candidatos')) return Promise.resolve({ data: candidatosMock });
      if (url.includes('/solicitudes')) return Promise.resolve({ data: solicitudesMock });
      return Promise.reject(new Error('Ruta no encontrada'));
    });

    render(<MemoryRouter><Dashboard /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByText('Ana López')).toBeInTheDocument();
      expect(screen.getByText('Carlos Vera')).toBeInTheDocument();
    });

    const indicadores = within(screen.getByRole('region', { name: /indicadores de evaluaciones/i }));
    const tarjetaTotal = indicadores.getByText('Total candidatos').closest('article');
    const tarjetaPendientes = indicadores.getByText('Pendientes').closest('article');
    const tarjetaEnProceso = indicadores.getByText('En proceso').closest('article');
    const tarjetaFinalizadas = indicadores.getByText('Finalizadas').closest('article');

    expect(within(tarjetaTotal).getByText('2')).toBeInTheDocument();
    expect(within(tarjetaPendientes).getByText('1')).toBeInTheDocument();
    expect(within(tarjetaEnProceso).getByText('1')).toBeInTheDocument();
    expect(within(tarjetaFinalizadas).getByText('2')).toBeInTheDocument();

    expect(screen.getByText('Total de solicitudes: 4')).toBeInTheDocument();
    expect(screen.getByRole('progressbar', { name: 'Finalizadas' })).toHaveAttribute('aria-valuenow', '2');
  });

  it('debe filtrar el listado de candidatos por búsqueda ignorando tildes', async () => {
    api.get.mockImplementation((url) => {
      if (url.includes('/candidatos')) return Promise.resolve({ data: candidatosMock });
      if (url.includes('/solicitudes')) return Promise.resolve({ data: solicitudesMock });
      return Promise.reject(new Error('Ruta no encontrada'));
    });

    render(<MemoryRouter><Dashboard /></MemoryRouter>);

    await waitFor(() => expect(screen.getByText('Ana López')).toBeInTheDocument());

    const buscador = screen.getByRole('searchbox', { name: /buscar candidatos/i });
    fireEvent.change(buscador, { target: { value: 'lopez' } });

    expect(screen.getByText('Ana López')).toBeInTheDocument();
    expect(screen.queryByText('Carlos Vera')).not.toBeInTheDocument();
    expect(screen.getByText('1 candidato(s)')).toBeInTheDocument();

    fireEvent.change(buscador, { target: { value: 'inexistente' } });
    expect(screen.getByText('Sin coincidencias')).toBeInTheDocument();
  });

  it('debe mostrar alertas si falla la carga de candidatos o solicitudes', async () => {
    api.get.mockRejectedValue(new Error('Error de red'));

    render(<MemoryRouter><Dashboard /></MemoryRouter>);

    await waitFor(() => {
      const alertas = screen.getAllByRole('alert');
      expect(alertas[0]).toHaveTextContent(/no se pudo cargar el listado de candidatos/i);
      expect(alertas[0]).toHaveTextContent(/no se pudieron cargar los indicadores de solicitudes/i);
    });
  });
});
