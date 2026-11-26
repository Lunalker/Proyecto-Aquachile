import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import DocumentosCandidato from '../DocumentosCandidato';
import api from '../../services/api';
import * as AuthContext from '../../context/AuthContext';

vi.mock('../../services/api', () => ({
  default: { get: vi.fn(), post: vi.fn(), delete: vi.fn() },
}));
vi.mock('../../context/AuthContext', () => ({ useAuth: vi.fn() }));

function renderizar(state = { nombre: 'Camila Soto' }) {
  return render(
    <MemoryRouter initialEntries={[{ pathname: '/documentos/3', state }]}>
      <Routes>
        <Route path="/documentos/:idCandidato" element={<DocumentosCandidato />} />
      </Routes>
    </MemoryRouter>
  );
}

const meta = {
  id: 12,
  candidatoId: 3,
  nombreArchivo: 'CV_demo.pdf',
  tipoContenido: 'application/pdf',
  tamanoBytes: 2100,
  fechaCarga: '2026-11-07T15:30:00',
};

describe('Módulo de CV auténticos - DocumentosCandidato', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    AuthContext.useAuth.mockReturnValue({ usuario: { rol: 'ANALISTA' } });
    URL.createObjectURL = vi.fn(() => 'blob:http://localhost/cv-mock');
    URL.revokeObjectURL = vi.fn();
    window.confirm = vi.fn(() => true);
  });

  it('muestra el formulario al Analista si no existe un CV y el nombre del candidato', async () => {
    api.get.mockRejectedValueOnce({ response: { status: 404 } });
    renderizar();

    expect(await screen.findByText(/todavía no tiene un CV adjunto/i)).toBeInTheDocument();
    expect(screen.getByText(/Camila Soto · Archivo asociado/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/seleccionar documento pdf o word/i)).toBeInTheDocument();
  });

  it('sube un CV real mediante FormData y muestra el resultado', async () => {
    api.get.mockRejectedValueOnce({ response: { status: 404 } });
    api.post.mockResolvedValueOnce({ data: meta });
    const user = userEvent.setup();
    renderizar();

    const selector = await screen.findByLabelText(/seleccionar documento pdf o word/i);
    const cv = new File(['%PDF-1.4contenido ficticio'], 'CV_demo.pdf', { type: 'application/pdf' });
    await user.upload(selector, cv);
    await user.click(screen.getByRole('button', { name: /subir cv/i }));

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith('/documentos/candidatos/3', expect.any(FormData));
      const [url, datos] = api.post.mock.calls[0];
      expect(url).toBe('/documentos/candidatos/3');
      expect(datos.get('archivo').name).toBe('CV_demo.pdf');
      expect(screen.getByRole('status')).toHaveTextContent(/guardado correctamente/i);
    });
  });

  it('impide seleccionar un archivo con extensión no permitida o mayor a 5 MB', async () => {
    api.get.mockRejectedValueOnce({ response: { status: 404 } });
    const user = userEvent.setup({ applyAccept: false });
    renderizar();

    const selector = await screen.findByLabelText(/seleccionar documento pdf o word/i);
    await user.upload(selector, new File(['texto'], 'nota.txt', { type: 'text/plain' }));
    expect(screen.getByRole('alert')).toHaveTextContent(/solo se aceptan documentos/i);
    expect(screen.getByRole('button', { name: /subir cv/i })).toBeDisabled();

    const archivoGrande = new File(['x'], 'pesado.pdf', { type: 'application/pdf' });
    Object.defineProperty(archivoGrande, 'size', { value: 6 * 1024 * 1024 });
    await user.upload(selector, archivoGrande);
    expect(screen.getByRole('alert')).toHaveTextContent(/entre 1 byte y 5 MB/i);
  });

  it('permite descargar y eliminar el CV al Analista', async () => {
    api.get.mockImplementation((url) => {
      if (url.endsWith('/descargar')) return Promise.resolve({ data: new Uint8Array([1, 2, 3]) });
      return Promise.resolve({ data: meta });
    });
    api.delete.mockResolvedValueOnce({});
    const user = userEvent.setup();
    renderizar();

    expect(await screen.findByText('CV_demo.pdf')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /reemplazar currículum/i })).toBeInTheDocument();

    // Descargar CV
    await user.click(screen.getByRole('button', { name: /descargar cv/i }));
    await waitFor(() => {
      expect(api.get).toHaveBeenCalledWith('/documentos/candidatos/3/descargar', { responseType: 'blob' });
      expect(URL.createObjectURL).toHaveBeenCalled();
    });

    // Eliminar CV
    await user.click(screen.getByRole('button', { name: /eliminar cv/i }));
    await waitFor(() => {
      expect(window.confirm).toHaveBeenCalled();
      expect(api.delete).toHaveBeenCalledWith('/documentos/candidatos/3');
      expect(screen.getByRole('status')).toHaveTextContent(/eliminó correctamente/i);
      expect(screen.getByText(/todavía no tiene un CV adjunto/i)).toBeInTheDocument();
    });
  });

  it('el Evaluador puede consultar y descargar el archivo, pero no subir ni eliminar', async () => {
    AuthContext.useAuth.mockReturnValue({ usuario: { rol: 'EVALUADOR' } });
    api.get.mockResolvedValueOnce({ data: meta });
    renderizar();

    expect(await screen.findByText('CV_demo.pdf')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /descargar cv/i })).toBeInTheDocument();
    expect(screen.queryByLabelText(/seleccionar documento/i)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /eliminar cv/i })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: /volver/i })).toHaveAttribute('href', '/solicitudes');
  });
});
