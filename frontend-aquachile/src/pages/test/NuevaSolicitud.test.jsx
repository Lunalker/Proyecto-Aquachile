import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import NuevaSolicitud from '../NuevaSolicitud';
import api from '../../services/api';

vi.mock('../../services/api', () => ({
  default: {
    post: vi.fn(),
  },
}));

describe('Vista NuevaSolicitud', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  const renderConRuta = () => {
    return render(
      <MemoryRouter initialEntries={['/nueva-solicitud/5']}>
        <Routes>
          <Route path="/nueva-solicitud/:idCandidato" element={<NuevaSolicitud />} />
          <Route path="/solicitudes" element={<div>Listado de Solicitudes</div>} />
          <Route path="/" element={<div>Panel Principal</div>} />
        </Routes>
      </MemoryRouter>
    );
  };

  it('debe registrar una nueva solicitud con observaciones para el candidato #5 y redirigir', async () => {
    api.post.mockResolvedValueOnce({ data: { id: 1 } });

    renderConRuta();

    fireEvent.change(screen.getByLabelText(/cargo a evaluar/i), { target: { value: 'Jefe de Turno' } });
    fireEvent.change(screen.getByLabelText(/familia de cargo/i), { target: { value: 'Producción' } });
    fireEvent.change(screen.getByLabelText(/profesional responsable/i), { target: { value: 'Psicólogo Juan' } });
    fireEvent.change(screen.getByLabelText(/observaciones iniciales/i), { target: { value: 'Prioridad alta' } });

    fireEvent.click(screen.getByRole('button', { name: /guardar solicitud/i }));

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith('/solicitudes', {
        cargo: 'Jefe de Turno',
        familiaCargo: 'Producción',
        profesionalResponsable: 'Psicólogo Juan',
        observaciones: 'Prioridad alta',
        candidato: { id: 5 },
      });
      expect(screen.getByText('Listado de Solicitudes')).toBeInTheDocument();
    });
  });

  it('debe mostrar mensaje de error si el usuario no tiene permisos (403)', async () => {
    api.post.mockRejectedValueOnce({ response: { status: 403 } });

    renderConRuta();

    fireEvent.change(screen.getByLabelText(/cargo a evaluar/i), { target: { value: 'Jefe de Turno' } });
    fireEvent.change(screen.getByLabelText(/familia de cargo/i), { target: { value: 'Producción' } });
    fireEvent.change(screen.getByLabelText(/profesional responsable/i), { target: { value: 'Psicólogo Juan' } });
    fireEvent.click(screen.getByRole('button', { name: /guardar solicitud/i }));

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('No tienes permisos para crear solicitudes.');
    });
  });

  it('debe permitir cancelar y volver al panel principal', () => {
    renderConRuta();

    fireEvent.click(screen.getByRole('button', { name: /cancelar/i }));
    expect(screen.getByText('Panel Principal')).toBeInTheDocument();
  });
});
