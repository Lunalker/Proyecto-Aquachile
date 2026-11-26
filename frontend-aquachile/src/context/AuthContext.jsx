import { createContext, useContext, useEffect, useState } from 'react';
import api, { asegurarCsrf, invalidarCsrf } from '../services/api';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [usuario, setUsuario] = useState(null);
  const [cargando, setCargando] = useState(true);
  const [errorConexion, setErrorConexion] = useState('');

  useEffect(() => {
    let activo = true;
    async function comprobarSesion() {
      try {
        const respuesta = await api.get('/auth/me');
        if (activo) setUsuario(respuesta.data);
      } catch (error) {
        if (!activo) return;
        setUsuario(null);
        if (error.response?.status !== 401) {
          setErrorConexion('No se puede conectar con Spring Boot.');
        }
      } finally {
        if (activo) setCargando(false);
      }
    }
    const expirada = () => {
      setUsuario(null);
      invalidarCsrf();
    };
    window.addEventListener('aquachile:sesion-expirada', expirada);
    comprobarSesion();
    return () => {
      activo = false;
      window.removeEventListener('aquachile:sesion-expirada', expirada);
    };
  }, []);

  async function iniciarSesion(usuarioIngresado, clave) {
    const respuesta = await api.post('/auth/login', {
      usuario: usuarioIngresado.trim(),
      clave,
    });
    invalidarCsrf();
    await asegurarCsrf({ renovar: true });
    setUsuario(respuesta.data);
    setErrorConexion('');
    return respuesta.data;
  }

  async function cerrarSesion() {
    try {
      await api.post('/auth/logout');
    } finally {
      invalidarCsrf();
      setUsuario(null);
    }
  }

  return (
      <AuthContext.Provider value={{ usuario, cargando, errorConexion, iniciarSesion, cerrarSesion }}>
        {children}
      </AuthContext.Provider>
  );
}

export function useAuth() {
  const contexto = useContext(AuthContext);
  if (!contexto) throw new Error('useAuth debe utilizarse dentro de AuthProvider');
  return contexto;
}