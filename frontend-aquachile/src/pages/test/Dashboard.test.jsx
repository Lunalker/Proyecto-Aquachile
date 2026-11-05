import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import Dashboard from '../Dashboard';
import api from '../../services/api';

vi.mock('../../services/api', () => ({
  default: {
    get: vi.fn(),
  },
}));

// Mock del componente TarjetaIndicador
vi.mock('../../components/TarjetaIndicador', () => ({
  default: ({ titulo, valor }) => (
    <div data-testid="indicador">
      <span>{titulo}</span>: <span>{valor}</span>
    </div>
  ),
}));

describe('Vista Dashboard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('debe mostrar el estado de carga al iniciar', () => {
    api.get.mockImplementation(() => new Promise(() => {})); // Promesa sin resolver

    render(
      <MemoryRouter>
        <Dashboard />
      </MemoryRouter>
    );

    expect(screen.getByRole('status')).toHaveTextContent('Cargando datos...');
  });

  it('debe cargar y mostrar la lista de candidatos e indicadores correctamente', async () => {
    const candidatosMock = [{ id: 1, nombre: 'Juan Pérez', correo: 'juan@test.com', cargoPostula: 'Analista', origen: 'Externo' }];
    const solicitudesMock = [{ id: 101, estado: 'Pendiente' }, { id: 102, estado: 'En proceso' }];

    api.get.mockImplementation((url) => {
      if (url.includes('/candidatos')) return Promise.resolve({ data: candidatosMock });
      if (url.includes('/solicitudes')) return Promise.resolve({ data: solicitudesMock });
      return Promise.reject(new Error('Ruta no válida'));
    });

    render(
      <MemoryRouter>
        <Dashboard />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Juan Pérez')).toBeInTheDocument();
      expect(screen.getByText('juan@test.com')).toBeInTheDocument();
    });
  });

  it('debe mostrar mensaje de error si la API de candidatos falla', async () => {
    api.get.mockRejectedValue(new Error('Error de red'));

    render(
      <MemoryRouter>
        <Dashboard />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('No se pudieron cargar los candidatos.');
    });
  });
});