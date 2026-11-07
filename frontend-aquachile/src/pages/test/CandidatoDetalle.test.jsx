import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import CandidatoDetalle from '../CandidatoDetalle';
import api from '../../services/api';

vi.mock('../../services/api', () => ({
  default: {
    get: vi.fn(),
    put: vi.fn(),
  },
}));

const candidatoMock = {
  id: 1,
  nombre: 'María González',
  correo: 'maria@aquachile.cl',
  telefono: '912345678',
  cargoPostula: 'Jefa de Planta',
  familiaCargo: 'Operaciones',
  origen: 'Interno',
  ubicacion: 'Puerto Montt',
  unidad: 'Procesamiento',
};

describe('Vista CandidatoDetalle', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  const renderConRuta = (id = '1') => {
    return render(
      <MemoryRouter initialEntries={[`/candidatos/${id}`]}>
        <Routes>
          <Route path="/candidatos/:idCandidato" element={<CandidatoDetalle />} />
        </Routes>
      </MemoryRouter>
    );
  };

  it('debe cargar y desplegar los datos del candidato', async () => {
    api.get.mockResolvedValueOnce({ data: candidatoMock });

    renderConRuta();

    await waitFor(() => {
      expect(screen.getByText('María González')).toBeInTheDocument();
      expect(screen.getByText('maria@aquachile.cl')).toBeInTheDocument();
    });
  });

  it('debe permitir habilitar la edición y guardar los cambios', async () => {
    api.get.mockResolvedValueOnce({ data: candidatoMock });
    api.put.mockResolvedValueOnce({ data: { ...candidatoMock, nombre: 'María González Updated' } });

    renderConRuta();

    await waitFor(() => expect(screen.getByText('María González')).toBeInTheDocument());

    // Activar formulario de edición
    fireEvent.click(screen.getByRole('button', { name: /editar candidato/i }));

    const inputNombre = screen.getByLabelText(/nombre completo \*/i);
    fireEvent.change(inputNombre, { target: { value: 'María González Updated' } });

    // Enviar el formulario
    fireEvent.click(screen.getByRole('button', { name: /guardar cambios/i }));

    await waitFor(() => {
      expect(api.put).toHaveBeenCalledWith('/candidatos/1', expect.objectContaining({
        nombre: 'María González Updated',
      }));
      expect(screen.getByRole('status')).toHaveTextContent(/actualizaron correctamente/i);
    });
  });

  it('debe mostrar error 404 si el candidato no existe', async () => {
    api.get.mockRejectedValueOnce({ response: { status: 404 } });

    renderConRuta('999');

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('No se encontró el candidato solicitado.');
    });
  });
});