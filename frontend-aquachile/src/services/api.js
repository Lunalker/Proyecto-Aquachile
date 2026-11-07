import axios from 'axios';

// Todas las solicitudes deben pasar por esta instancia (cookie de sesión + CSRF).
const api = axios.create({
  baseURL: 'http://localhost:8080/api',
  withCredentials: true,
  timeout: 12000,
});

let csrfToken = null;
let csrfPromise = null;

export function invalidarCsrf() {
  csrfToken = null;
}

export async function asegurarCsrf({ renovar = false } = {}) {
  if (renovar) csrfToken = null;
  if (csrfToken) return csrfToken;
  if (!csrfPromise) {
    csrfPromise = api.get('/auth/csrf')
      .then((respuesta) => {
        csrfToken = respuesta.data.token;
        return csrfToken;
      })
      .finally(() => { csrfPromise = null; });
  }
  return csrfPromise;
}

api.interceptors.request.use(async (config) => {
  const metodo = (config.method || 'get').toLowerCase();
  if (['post', 'put', 'patch', 'delete'].includes(metodo)) {
    const token = await asegurarCsrf();
    config.headers.set('X-CSRF-TOKEN', token);
  }
  return config;
});

api.interceptors.response.use(
  (respuesta) => respuesta,
  (error) => {
    const url = String(error.config?.url || '');
    if (error.response?.status === 401 && !url.startsWith('/auth/')) {
      // Una sesión vencida obliga a entrar de nuevo; no se simula un logout.
      window.dispatchEvent(new Event('aquachile:sesion-expirada'));
    }
    return Promise.reject(error);
  }
);

export default api;
