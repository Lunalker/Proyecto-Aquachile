import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import ListadoSolicitudes from '../ListadoSolicitudes';
import api from '../../services/api';
import * as AuthContext from '../../context/AuthContext';

vi.mock('../../services/api', () => ({
  default: {
    get: vi.fn(),
  },
}));

vi.mock('../../components/EstadoBadge', () => ({
  default: ({ estado }) => <span>{estado}</span>,
}));

const solicitudesMock = [
  { id: 1, candidato: { nombre: 'Carlos Ruiz' }, cargo: 'Supervisor', familiaCargo: 'Mantenimiento', profesionalResponsable: 'Ana Evaluadora', fechaSolicitud: '2026-03-01', estado: 'Pendiente' },
  { id: 2, candidato: { nombre: 'Laura Soto' }, cargo: 'Veterinaria', familiaCargo: 'Salud', profesionalResponsable: 'Pedro Evaluador', fechaSolicitud: '2026-03-02', estado: 'Finalizada' },
];

describe('Vista ListadoSolicitudes', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(AuthContext, 'useAuth').mockReturnValue({ usuario: { rol: 'EVALUADOR' } });
  });

  it('debe listar las solicitudes y permitir filtrar por búsqueda', async () => {
    api.get.mockResolvedValueOnce({ data: solicitudesMock });

    render(
      <MemoryRouter>
        <ListadoSolicitudes />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Carlos Ruiz')).toBeInTheDocument();
      expect(screen.getByText('Laura Soto')).toBeInTheDocument();
    });

    // Escribir en la barra de búsqueda
    const inputBuscar = screen.getByLabelText(/buscar solicitud/i);
    fireEvent.change(inputBuscar, { target: { value: 'Carlos' } });

    expect(screen.getByText('Carlos Ruiz')).toBeInTheDocument();
    expect(screen.queryByText('Laura Soto')).not.toBeInTheDocument();
  });

  it('debe filtrar por estado utilizando el select', async () => {
    api.get.mockResolvedValueOnce({ data: solicitudesMock });

    render(
      <MemoryRouter>
        <ListadoSolicitudes />
      </MemoryRouter>
    );

    await waitFor(() => expect(screen.getByText('Carlos Ruiz')).toBeInTheDocument());

    const selectEstado = screen.getByLabelText(/estado/i);
    fireEvent.change(selectEstado, { target: { value: 'Finalizada' } });

    expect(screen.queryByText('Carlos Ruiz')).not.toBeInTheDocument();
    expect(screen.getByText('Laura Soto')).toBeInTheDocument();
  });

  it('debe mostrar el botón Gestionar si el usuario tiene rol EVALUADOR', async () => {
    api.get.mockResolvedValueOnce({ data: solicitudesMock });

    render(
      <MemoryRouter>
        <ListadoSolicitudes />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getAllByText('Gestionar').length).toBeGreaterThan(0);
    });
  });
});