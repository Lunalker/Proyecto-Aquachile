import axios from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8080/api',
  withCredentials: true,
  timeout: 12000,
});

let csrfToken = null;
let csrfPromise = null;

export async function asegurarCsrf() {
  if (csrfToken) return csrfToken;
  if (!csrfPromise) {
    csrfPromise = api
      .get('/auth/csrf')
      .then((res) => {
        csrfToken = res.data.token;
        return csrfToken;
      })
      .finally(() => {
        csrfPromise = null;
      });
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
  (res) => res,
  async (error) => {
    const original = error.config;
    if (error.response?.status === 403 && original && !original._reintentado) {
      original._reintentado = true;
      csrfToken = null;
      const nuevoToken = await asegurarCsrf();
      original.headers.set('X-CSRF-TOKEN', nuevoToken);
      return api(original);
    }
    return Promise.reject(error);
  }
);

export default api;