import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import Login from '../Login';
import * as AuthContext from '../../context/AuthContext';

// Simulación directa del hook de autenticación
vi.mock('../../context/AuthContext', () => ({
  useAuth: vi.fn(),
}));

describe('Pruebas unitarias e integradas - Login', () => {
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

  it('debe renderizar el formulario y sus campos principales', () => {
    renderLogin();

    expect(screen.getByLabelText(/usuario/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/contraseña/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /iniciar sesión/i })).toBeInTheDocument();
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

  it('debe mostrar una alerta de error ante credenciales incorrectas (HTTP 401)', async () => {
    const user = userEvent.setup();
    mockIniciarSesion.mockRejectedValueOnce({ response: { status: 401 } });

    renderLogin();

    await user.type(screen.getByLabelText(/usuario/i), 'usuario_invalido');
    await user.type(screen.getByLabelText(/contraseña/i), 'clave_erronea');
    await user.click(screen.getByRole('button', { name: /iniciar sesión/i }));

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('Usuario o contraseña incorrectos.');
    });
  });
});