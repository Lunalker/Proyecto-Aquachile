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
        </Routes>
      </MemoryRouter>
    );
  };

  it('debe enviar la evaluación y actualizar el estado de la solicitud', async () => {
    api.post.mockResolvedValueOnce({ data: { id: 1 } });
    api.put.mockResolvedValueOnce({ data: { id: 10, estado: 'Finalizada' } });

    renderConRuta();

    // Completar campo de texto
    const textareaResultado = screen.getByLabelText(/resultado y observaciones/i);
    fireEvent.change(textareaResultado, { target: { value: 'Candidato cumple con el perfil solicitado.' } });

    // Presionar botón de guardado
    fireEvent.click(screen.getByRole('button', { name: /guardar evaluación/i }));

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith('/evaluaciones', {
        resultado: 'Candidato cumple con el perfil solicitado.',
        estadoEvaluacion: 'Recomendado',
        solicitud: { id: 10 },
      });

      expect(api.put).toHaveBeenCalledWith(
        '/solicitudes/10/estado',
        'Finalizada',
        { headers: { 'Content-Type': 'text/plain' } }
      );
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
});