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

  it('debe cargar y desplegar los datos del candidato y sus accesos directos', async () => {
    api.get.mockResolvedValueOnce({ data: candidatoMock });

    renderConRuta();

    await waitFor(() => {
      expect(screen.getByText('María González')).toBeInTheDocument();
      expect(screen.getByText('maria@aquachile.cl')).toBeInTheDocument();
      expect(screen.getByText('Jefa de Planta')).toBeInTheDocument();
    });

    const linkDocumentos = screen.getByRole('link', { name: /documentos \/ cv/i });
    expect(linkDocumentos).toHaveAttribute('href', '/documentos/1');

    const linkSolicitud = screen.getByRole('link', { name: /crear solicitud/i });
    expect(linkSolicitud).toHaveAttribute('href', '/nueva-solicitud/1');
  });

  it('debe permitir habilitar la edición y guardar los cambios', async () => {
    api.get.mockResolvedValueOnce({ data: candidatoMock });
    api.put.mockResolvedValueOnce({ data: { ...candidatoMock, nombre: 'María González Updated' } });

    renderConRuta();

    await waitFor(() => expect(screen.getByText('María González')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: /editar candidato/i }));

    const inputNombre = screen.getByLabelText(/nombre completo \*/i);
    fireEvent.change(inputNombre, { target: { value: 'María González Updated' } });

    fireEvent.click(screen.getByRole('button', { name: /guardar cambios/i }));

    await waitFor(() => {
      expect(api.put).toHaveBeenCalledWith('/candidatos/1', expect.objectContaining({
        nombre: 'María González Updated',
      }));
      expect(screen.getByRole('status')).toHaveTextContent(/actualizaron correctamente/i);
    });
  });

  it('debe cancelar la edición restaurando los datos originales', async () => {
    api.get.mockResolvedValueOnce({ data: candidatoMock });

    renderConRuta();

    await waitFor(() => expect(screen.getByText('María González')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: /editar candidato/i }));
    const inputNombre = screen.getByLabelText(/nombre completo \*/i);
    fireEvent.change(inputNombre, { target: { value: 'Cambio Temporal' } });

    fireEvent.click(screen.getByRole('button', { name: /cancelar/i }));

    expect(screen.getByText('María González')).toBeInTheDocument();
    expect(api.put).not.toHaveBeenCalled();
  });

  it('debe validar campos obligatorios antes de enviar la actualización', async () => {
    api.get.mockResolvedValueOnce({ data: candidatoMock });

    renderConRuta();

    await waitFor(() => expect(screen.getByText('María González')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: /editar candidato/i }));
    fireEvent.change(screen.getByLabelText(/nombre completo \*/i), { target: { value: '' } });
    fireEvent.change(screen.getByLabelText(/correo electrónico \*/i), { target: { value: 'correo-invalido' } });

    fireEvent.click(screen.getByRole('button', { name: /guardar cambios/i }));

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/revisa los campos marcados antes de guardar/i);
    });
    expect(api.put).not.toHaveBeenCalled();
  });

  it('debe mostrar mensaje de conflicto (409) si el correo ya pertenece a otro candidato', async () => {
    api.get.mockResolvedValueOnce({ data: candidatoMock });
    api.put.mockRejectedValueOnce({ response: { status: 409 } });

    renderConRuta();

    await waitFor(() => expect(screen.getByText('María González')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: /editar candidato/i }));
    fireEvent.click(screen.getByRole('button', { name: /guardar cambios/i }));

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/correo no pertenezca a otro candidato/i);
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
