import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { AuthProvider, useAuth } from '../../context/AuthContext';
import api from '../../services/api';

vi.mock('../../services/api', () => ({
    default: {
        get: vi.fn(),
        post: vi.fn(),
    },
    asegurarCsrf: vi.fn().mockResolvedValue('csrf-test'),
    invalidarCsrf: vi.fn(),
}));

function ConsumidorSesion() {
    const { usuario, cargando } = useAuth();
    if (cargando) return <p>Cargando sesión...</p>;
    return <div>{usuario ? `Sesión activa: ${usuario.usuario} (${usuario.rol})` : 'Sin sesión'}</div>;
}

describe('AuthContext - Persistencia JWT con cookie HttpOnly', () => {
    it('recupera la sesión activa desde /auth/me al recargar sin usar localStorage ni sessionStorage', async () => {
        const spyLocal = vi.spyOn(Storage.prototype, 'setItem');
        api.get.mockResolvedValueOnce({ data: { usuario: 'analista.demo', rol: 'ANALISTA' } });

        render(
            <AuthProvider>
                <ConsumidorSesion />
            </AuthProvider>
        );

        await waitFor(() => {
            expect(screen.getByText('Sesión activa: analista.demo (ANALISTA)')).toBeInTheDocument();
        });
        expect(api.get).toHaveBeenCalledWith('/auth/me');
        expect(spyLocal).not.toHaveBeenCalled();
        spyLocal.mockRestore();
    });

    it('mantiene estado no autenticado cuando /auth/me responde 401', async () => {
        api.get.mockRejectedValueOnce({ response: { status: 401 } });

        render(
            <AuthProvider>
                <ConsumidorSesion />
            </AuthProvider>
        );

        await waitFor(() => {
            expect(screen.getByText('Sin sesión')).toBeInTheDocument();
        });
    });
});