import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { describe, it, expect, vi } from 'vitest';
import axios from 'axios';
import Login from '../Login';
import { AuthProvider } from '../../context/AuthContext'; // Importa tu AuthProvider (ajusta la ruta si es necesario)

// Mock de Axios
vi.mock('axios', () => {
  const instance = {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    interceptors: {
      request: { use: vi.fn(), eject: vi.fn() },
      response: { use: vi.fn(), eject: vi.fn() },
    },
  };

  return {
    default: {
      ...instance,
      create: vi.fn(() => instance),
    },
  };
});

describe('Componente Login', () => {
  it('debe renderizar y procesar el inicio de sesión correctamente', async () => {
    axios.post.mockResolvedValueOnce({
      data: { usuario: 'analista', rol: 'ANALISTA' }
    });

    render(
      <AuthProvider>
        <BrowserRouter>
          <Login />
        </BrowserRouter>
      </AuthProvider>
    );

    // Aquí van tus comprobaciones/interacciones
  });
});