import { render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import Dashboard from '../Dashboard';
import api from '../../services/api';

vi.mock('../../services/api', () => ({
  default: {
    get: vi.fn(),
  },
}));

describe('Pruebas integradas - Dashboard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('debe procesar e impresionar las tarjetas indicadoras y el listado de candidatos', async () => {
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

    api.get.mockImplementation((url) => {
      if (url.includes('/candidatos')) return Promise.resolve({ data: candidatosMock });
      if (url.includes('/solicitudes')) return Promise.resolve({ data: solicitudesMock });
      return Promise.reject(new Error('Ruta no encontrada'));
    });

    render(
      <MemoryRouter>
        <Dashboard />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Ana López')).toBeInTheDocument();
      expect(screen.getByText('Carlos Vera')).toBeInTheDocument();
    });

    // Validar cada número dentro de su respectiva tarjeta <article>
    const tarjetaTotal = screen.getByText('Total candidatos').closest('article');
    expect(within(tarjetaTotal).getByText('2')).toBeInTheDocument();

    const tarjetaPendientes = screen.getByText('Pendientes').closest('article');
    expect(within(tarjetaPendientes).getByText('1')).toBeInTheDocument();

    const tarjetaFinalizadas = screen.getByText('Finalizadas').closest('article');
    expect(within(tarjetaFinalizadas).getByText('2')).toBeInTheDocument();
  });
});