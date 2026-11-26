import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import Login from '../Login';
import api from '../../services/api';
import * as AuthContext from '../../context/AuthContext';

vi.mock('../../context/AuthContext', () => ({
  useAuth: vi.fn(),
}));

vi.mock('../../services/api', () => ({
  default: {
    post: vi.fn(),
  },
}));

describe('Pruebas unitarias e integradas - Login, Registro y Verificación', () => {
  let mockIniciarSesion;

  beforeEach(() => {
    vi.clearAllMocks();
    mockIniciarSesion = vi.fn();
    AuthContext.useAuth.mockReturnValue({
      usuario: null,
      cargando: false,
      iniciarSesion: mockIniciarSesion,
      errorConexion: '',
    });
  });

  const renderLogin = () => {
    return render(
      <MemoryRouter initialEntries={['/login']}>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/" element={<div>Dashboard Analista</div>} />
          <Route path="/solicitudes" element={<div>Panel Evaluador</div>} />
        </Routes>
      </MemoryRouter>
    );
  };

  it('debe renderizar el formulario de login y el botón de Registro', () => {
    renderLogin();

    expect(screen.getByLabelText(/usuario/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/contraseña/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /iniciar sesión/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^registro$/i })).toBeInTheDocument();
  });

  it('debe mostrar mensaje de carga cuando la verificación inicial está activa', () => {
    AuthContext.useAuth.mockReturnValue({
      usuario: null,
      cargando: true,
      iniciarSesion: mockIniciarSesion,
    });

    renderLogin();
    expect(screen.getByText(/comprobando sesión/i)).toBeInTheDocument();
  });

  it('debe permitir ingresar credenciales y redirigir al Dashboard de Analista', async () => {
    const user = userEvent.setup();
    mockIniciarSesion.mockResolvedValueOnce({ usuario: 'analista', rol: 'ANALISTA' });

    renderLogin();

    await user.type(screen.getByLabelText(/usuario/i), 'analista');
    await user.type(screen.getByLabelText(/contraseña/i), 'admin123');
    await user.click(screen.getByRole('button', { name: /iniciar sesión/i }));

    expect(mockIniciarSesion).toHaveBeenCalledWith('analista', 'admin123');
    await waitFor(() => {
      expect(screen.getByText('Dashboard Analista')).toBeInTheDocument();
    });
  });

  it('debe redirigir al panel de solicitudes si el rol devuelto es EVALUADOR', async () => {
    const user = userEvent.setup();
    mockIniciarSesion.mockResolvedValueOnce({ usuario: 'evaluador', rol: 'EVALUADOR' });

    renderLogin();

    await user.type(screen.getByLabelText(/usuario/i), 'evaluador');
    await user.type(screen.getByLabelText(/contraseña/i), 'user123');
    await user.click(screen.getByRole('button', { name: /iniciar sesión/i }));

    await waitFor(() => {
      expect(screen.getByText('Panel Evaluador')).toBeInTheDocument();
    });
  });

  it('debe mostrar una alerta de error ante credenciales incorrectas o cuenta no verificada (HTTP 401)', async () => {
    const user = userEvent.setup();
    mockIniciarSesion.mockRejectedValueOnce({ response: { status: 401 } });

    renderLogin();

    await user.type(screen.getByLabelText(/usuario/i), 'usuario_invalido');
    await user.type(screen.getByLabelText(/contraseña/i), 'clave_erronea');
    await user.click(screen.getByRole('button', { name: /iniciar sesión/i }));

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('Usuario o contraseña incorrectos (o cuenta aún no verificada).');
    });
  });

  it('debe completar el flujo de registro de nuevo usuario, envío de código y verificación de correo', async () => {
    const user = userEvent.setup();
    api.post
      .mockResolvedValueOnce({ data: { mensaje: 'Código enviado' } })
      .mockResolvedValueOnce({ data: { mensaje: 'Reenviado' } })
      .mockResolvedValueOnce({ data: { username: 'camila.soto' } });

    renderLogin();

    // 1. Cambiar a modo registro
    await user.click(screen.getByRole('button', { name: /^registro$/i }));
    expect(screen.getByRole('heading', { name: /registro de usuario/i })).toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText(/rol del usuario/i), 'EVALUADOR');
    await user.type(screen.getByLabelText(/nombre completo/i), 'Camila Soto');
    await user.type(screen.getByLabelText(/correo electrónico/i), 'Camila@Ejemplo.cl');
    await user.type(screen.getByLabelText(/^usuario$/i), 'camila.soto');
    await user.type(screen.getByLabelText(/contraseña/i), 'clave123');

    await user.click(screen.getByRole('button', { name: /continuar y enviar código/i }));

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith('/auth/registro', {
        rol: 'EVALUADOR',
        nombreCompleto: 'Camila Soto',
        correo: 'camila@ejemplo.cl',
        username: 'camila.soto',
        password: 'clave123',
      });
      expect(screen.getByRole('heading', { name: /ingresa tu código/i })).toBeInTheDocument();
      expect(screen.getByRole('status')).toHaveTextContent(/enviamos un código de 6 dígitos a camila@ejemplo\.cl/i);
    });

    // 2. Reenviar código
    await user.click(screen.getByRole('button', { name: /reenviar código/i }));
    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith('/auth/reenviar-codigo', { correo: 'camila@ejemplo.cl' });
    });

    // 3. Ingresar código de 6 dígitos y verificar
    const btnVerificar = screen.getByRole('button', { name: /verificar cuenta/i });
    expect(btnVerificar).toBeDisabled();

    await user.type(screen.getByLabelText(/código de 6 dígitos/i), '123456');
    expect(btnVerificar).not.toBeDisabled();

    await user.click(btnVerificar);

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith('/auth/verificar-codigo', {
        correo: 'camila@ejemplo.cl',
        codigo: '123456',
      });
      expect(screen.getByRole('heading', { name: /iniciar sesión/i })).toBeInTheDocument();
      expect(screen.getByRole('status')).toHaveTextContent(/correo verificado correctamente/i);
      expect(screen.getByLabelText(/usuario/i)).toHaveValue('camila.soto');
    });
  });

  it('debe mostrar error si el registro falla en el backend', async () => {
    const user = userEvent.setup();
    api.post.mockRejectedValueOnce({ response: { data: { error: 'El correo ya está registrado.' } } });

    renderLogin();

    await user.click(screen.getByRole('button', { name: /^registro$/i }));
    await user.type(screen.getByLabelText(/nombre completo/i), 'Ana López');
    await user.type(screen.getByLabelText(/correo electrónico/i), 'ana@ejemplo.cl');
    await user.type(screen.getByLabelText(/^usuario$/i), 'analopez');
    await user.type(screen.getByLabelText(/contraseña/i), 'clave123');
    await user.click(screen.getByRole('button', { name: /continuar y enviar código/i }));

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('El correo ya está registrado.');
    });
  });
});
