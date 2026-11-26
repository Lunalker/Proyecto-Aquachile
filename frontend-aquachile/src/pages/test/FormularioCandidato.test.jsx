import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import FormularioCandidato from '../FormularioCandidato';
import api from '../../services/api';

vi.mock('../../services/api', () => ({
  default: {
    post: vi.fn(),
    get: vi.fn(),
  },
}));

const renderFormulario = () => render(
  <MemoryRouter><FormularioCandidato /></MemoryRouter>
);

describe('Pruebas unitarias e integradas - FormularioCandidato', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    URL.createObjectURL = vi.fn(() => 'blob:http://localhost/cv-local');
    URL.revokeObjectURL = vi.fn();
    window.open = vi.fn();
  });

  it('debe renderizar correctamente todos los campos, el selector de CV y botones', () => {
    renderFormulario();

    expect(screen.getByLabelText(/nombre completo/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/correo electrónico/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/cargo al que postula/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/origen del candidato/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/currículum vitae \(opcional\)/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /guardar candidato/i })).toBeInTheDocument();
  });

  it('debe validar los campos obligatorios antes de enviar al servidor', async () => {
    const user = userEvent.setup();
    renderFormulario();

    await user.click(screen.getByRole('button', { name: /guardar candidato/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/revisa los campos marcados antes de guardar/i);
    expect(api.post).not.toHaveBeenCalled();
  });

  it('debe enviar los datos del candidato sin CV y reiniciar el formulario tras guardar', async () => {
    const user = userEvent.setup();
    api.post.mockResolvedValueOnce({ data: { id: 1 } });

    renderFormulario();

    const inputNombre = screen.getByLabelText(/nombre completo/i);
    const inputCorreo = screen.getByLabelText(/correo electrónico/i);
    const inputCargo = screen.getByLabelText(/cargo al que postula/i);
    const selectOrigen = screen.getByLabelText(/origen del candidato/i);

    await user.type(inputNombre, 'Pedro Pascal');
    await user.type(inputCorreo, 'pedro@aquachile.cl');
    await user.type(inputCargo, 'Gerente de Operaciones');
    await user.selectOptions(selectOrigen, 'Interno');
    await user.click(screen.getByRole('button', { name: /guardar candidato/i }));

    expect(api.post).toHaveBeenCalledWith('/candidatos', expect.objectContaining({
      nombre: 'Pedro Pascal',
      correo: 'pedro@aquachile.cl',
      cargoPostula: 'Gerente de Operaciones',
      origen: 'Interno',
    }));

    await waitFor(() => {
      expect(screen.getByRole('status')).toHaveTextContent('¡Candidato registrado correctamente!');
    });

    expect(inputNombre).toHaveValue('');
    expect(inputCorreo).toHaveValue('');
  });

  it('debe adjuntar un CV válido, previsualizarlo, subirlo junto al candidato y permitir abrir el CV guardado', async () => {
    const user = userEvent.setup();
    api.post
      .mockResolvedValueOnce({ data: { id: 7 } })
      .mockResolvedValueOnce({
        data: { id: 20, nombreArchivo: 'CV_Camila.pdf', tipoContenido: 'application/pdf' },
      });
    api.get.mockResolvedValueOnce({ data: new Uint8Array([1, 2, 3]) });

    renderFormulario();

    await user.type(screen.getByLabelText(/nombre completo/i), 'Camila Soto');
    await user.type(screen.getByLabelText(/correo electrónico/i), 'camila@ejemplo.cl');
    await user.type(screen.getByLabelText(/cargo al que postula/i), 'Analista de Procesos');

    const inputCv = screen.getByLabelText(/currículum vitae \(opcional\)/i);
    const archivoPdf = new File(['%PDF-1.4contenido'], 'CV_Camila.pdf', { type: 'application/pdf' });
    await user.upload(inputCv, archivoPdf);

    expect(screen.getByText(/archivo seleccionado: CV_Camila\.pdf/i)).toBeInTheDocument();

    // Vista previa local antes de guardar
    await user.click(screen.getByRole('button', { name: /ver archivo/i }));
    expect(window.open).toHaveBeenCalledWith('blob:http://localhost/cv-local', '_blank', 'noopener,noreferrer');

    // Guardar candidato + subir CV
    await user.click(screen.getByRole('button', { name: /guardar candidato/i }));

    await waitFor(() => {
      expect(api.post).toHaveBeenNthCalledWith(1, '/candidatos', expect.objectContaining({ nombre: 'Camila Soto' }));
      expect(api.post).toHaveBeenNthCalledWith(2, '/documentos/candidatos/7', expect.any(FormData));
      expect(screen.getByRole('status')).toHaveTextContent(/registrado correctamente junto con su CV/i);
    });

    // Verificar botones post-registro
    expect(screen.getByRole('link', { name: /ver ficha/i })).toHaveAttribute('href', '/candidatos/7');
    await user.click(screen.getByRole('button', { name: /abrir cv \(CV_Camila\.pdf\)/i }));

    await waitFor(() => {
      expect(api.get).toHaveBeenCalledWith('/documentos/candidatos/7/descargar', { responseType: 'blob' });
    });
  });

  it('debe rechazar archivos con formato inválido o mayores a 5 MB', async () => {
    const user = userEvent.setup({ applyAccept: false });
    renderFormulario();

    const inputCv = screen.getByLabelText(/currículum vitae \(opcional\)/i);
    await user.upload(inputCv, new File(['img'], 'foto.png', { type: 'image/png' }));
    expect(screen.getByText(/solo se aceptan documentos pdf, doc o docx/i)).toBeInTheDocument();

    const archivoGrande = new File(['x'], 'cv_gigante.pdf', { type: 'application/pdf' });
    Object.defineProperty(archivoGrande, 'size', { value: 6 * 1024 * 1024 });
    await user.upload(inputCv, archivoGrande);
    expect(screen.getByText(/el archivo debe pesar entre 1 byte y 5 MB/i)).toBeInTheDocument();
  });

  it('debe notificar falta de permisos si el servidor retorna 403', async () => {
    const user = userEvent.setup();
    api.post.mockRejectedValueOnce({ response: { status: 403 } });

    renderFormulario();

    await user.type(screen.getByLabelText(/nombre completo/i), 'Juan Pérez');
    await user.type(screen.getByLabelText(/correo electrónico/i), 'juan@test.com');
    await user.type(screen.getByLabelText(/cargo al que postula/i), 'Analista');
    await user.click(screen.getByRole('button', { name: /guardar candidato/i }));

    await waitFor(() => {
      expect(screen.getByRole('status')).toHaveTextContent('Tu usuario no tiene permiso para registrar candidatos.');
    });
  });

  it('debe deshabilitar el botón de guardado mientras la petición está activa', async () => {
    const user = userEvent.setup();
    api.post.mockImplementation(() => new Promise(() => {}));

    renderFormulario();

    await user.type(screen.getByLabelText(/nombre completo/i), 'Candidato Bloqueo');
    await user.type(screen.getByLabelText(/correo electrónico/i), 'bloqueo@test.com');
    await user.type(screen.getByLabelText(/cargo al que postula/i), 'Desarrollador');

    await user.click(screen.getByRole('button', { name: /guardar candidato/i }));
    expect(screen.getByRole('button', { name: /guardando.../i })).toBeDisabled();
  });
});
