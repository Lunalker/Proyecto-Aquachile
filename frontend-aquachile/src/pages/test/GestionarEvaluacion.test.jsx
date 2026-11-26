import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import GestionarEvaluacion from '../GestionarEvaluacion';
import api from '../../services/api';

vi.mock('../../services/api', () => ({
  default: {
    post: vi.fn(),
    put: vi.fn(),
  },
}));

describe('Vista GestionarEvaluacion', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  const renderConRuta = () => {
    return render(
      <MemoryRouter initialEntries={['/evaluar/10']}>
        <Routes>
          <Route path="/evaluar/:idSolicitud" element={<GestionarEvaluacion />} />
          <Route path="/solicitudes" element={<div>Listado de Solicitudes</div>} />
        </Routes>
      </MemoryRouter>
    );
  };

  it('debe enviar la evaluación, actualizar el estado y redirigir a solicitudes', async () => {
    api.post.mockResolvedValueOnce({ data: { id: 1 } });
    api.put.mockResolvedValueOnce({ data: { id: 10, estado: 'En proceso' } });

    renderConRuta();

    fireEvent.change(screen.getByLabelText(/estado de la solicitud/i), {
      target: { value: 'En proceso' },
    });
    fireEvent.change(screen.getByLabelText(/categoría del evaluado/i), {
      target: { value: 'Recomendado con observaciones' },
    });
    fireEvent.change(screen.getByLabelText(/resultado y observaciones/i), {
      target: { value: 'Candidato cumple con el perfil solicitado.' },
    });

    fireEvent.click(screen.getByRole('button', { name: /guardar evaluación/i }));

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith('/evaluaciones', {
        resultado: 'Candidato cumple con el perfil solicitado.',
        estadoEvaluacion: 'Recomendado con observaciones',
        solicitud: { id: 10 },
      });

      expect(api.put).toHaveBeenCalledWith(
        '/solicitudes/10/estado',
        'En proceso',
        { headers: { 'Content-Type': 'text/plain' } }
      );

      expect(screen.getByText('Listado de Solicitudes')).toBeInTheDocument();
    });
  });

  it('debe mostrar mensaje de error si el usuario carece de permisos (403)', async () => {
    api.post.mockRejectedValueOnce({ response: { status: 403 } });

    renderConRuta();

    fireEvent.change(screen.getByLabelText(/resultado y observaciones/i), { target: { value: 'Prueba de permiso' } });
    fireEvent.click(screen.getByRole('button', { name: /guardar evaluación/i }));

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('No tienes permisos para gestionar evaluaciones.');
    });
  });

  it('debe mostrar aviso detallado si falla la actualización de estado tras crear la evaluación', async () => {
    api.post.mockResolvedValueOnce({ data: { id: 1 } });
    api.put.mockRejectedValueOnce({ response: { status: 500 } });

    renderConRuta();

    fireEvent.change(screen.getByLabelText(/resultado y observaciones/i), { target: { value: 'Observación de prueba' } });
    fireEvent.click(screen.getByRole('button', { name: /guardar evaluación/i }));

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/no se pudo completar la operación/i);
    });
  });

  it('debe permitir volver sin guardar a /solicitudes', () => {
    renderConRuta();

    fireEvent.click(screen.getByRole('button', { name: /volver sin guardar/i }));
    expect(screen.getByText('Listado de Solicitudes')).toBeInTheDocument();
  });
});
