import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import FormularioCandidato from '../FormularioCandidato';
import api from '../../services/api';

vi.mock('../../services/api', () => ({
  default: { post: vi.fn() },
}));

// El diseño profesional usa <Link>, por lo que necesita contexto de React Router.
const renderFormulario = () => render(
  <MemoryRouter><FormularioCandidato /></MemoryRouter>
);

describe('Pruebas unitarias - FormularioCandidato', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('debe renderizar correctamente todos los campos y botones', () => {
    renderFormulario();

    expect(screen.getByLabelText(/nombre completo/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/correo electrónico/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/cargo al que postula/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/origen del candidato/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /guardar candidato/i })).toBeInTheDocument();
  });

  it('debe enviar los datos del candidato y reiniciar el formulario tras guardar', async () => {
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
