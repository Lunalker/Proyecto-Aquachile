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
        </Routes>
      </MemoryRouter>
    );
  };

  it('debe registrar una nueva solicitud para el candidato #5', async () => {
    api.post.mockResolvedValueOnce({ data: { id: 1 } });

    renderConRuta();

    // Rellenar campos obligatorios
    fireEvent.change(screen.getByLabelText(/cargo a evaluar/i), { target: { value: 'Jefe de Turno' } });
    fireEvent.change(screen.getByLabelText(/familia de cargo/i), { target: { value: 'Producción' } });
    fireEvent.change(screen.getByLabelText(/profesional responsable/i), { target: { value: 'Psicólogo Juan' } });

    // Guardar
    fireEvent.click(screen.getByRole('button', { name: /guardar solicitud/i }));

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith('/solicitudes', {
        cargo: 'Jefe de Turno',
        familiaCargo: 'Producción',
        profesionalResponsable: 'Psicólogo Juan',
        observaciones: '',
        candidato: { id: 5 },
      });
    });
  });
});