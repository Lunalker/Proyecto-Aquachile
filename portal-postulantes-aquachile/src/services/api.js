import axios from 'axios';

const api = axios.create({
  baseURL: (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api').replace(/\/$/, ''),
  withCredentials: true,
  timeout: 45000,
});

let csrfToken = null;
let csrfPromise = null;

export async function asegurarCsrf({ renovar = false } = {}) {
  if (renovar) csrfToken = null;
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
  (respuesta) => respuesta,
  async (error) => {
    const config = error.config;
    if (
      error.response?.status === 403 &&
      config &&
      !config._reintentoCsrf &&
      ['post', 'put', 'patch', 'delete'].includes((config.method || '').toLowerCase())
    ) {
      config._reintentoCsrf = true;
      const nuevoToken = await asegurarCsrf({ renovar: true });
      config.headers.set('X-CSRF-TOKEN', nuevoToken);
      return api(config);
    }
    return Promise.reject(error);
  }
);

export async function listarOfertasPublicas() {
  const respuesta = await api.get('/ofertas/publicas'); // <-- CON BARRA (/), NO CON GUION (-)
  return respuesta.data;
}

export async function enviarPostulacionPublica({
  ofertaId,
  nombre,
  correo,
  telefono,
  ubicacion,
  unidad,
  fechaCita,
  horaCita,
  archivo,
}) {
  const formData = new FormData();
  formData.append('ofertaId', String(ofertaId));
  formData.append('nombre', nombre.trim());
  formData.append('correo', correo.trim());
  formData.append('telefono', telefono.trim());
  if (ubicacion && ubicacion.trim()) formData.append('ubicacion', ubicacion.trim());
  if (unidad && unidad.trim()) formData.append('unidad', unidad.trim());
  if (fechaCita && fechaCita.trim()) formData.append('fechaCita', fechaCita.trim());
  if (horaCita && horaCita.trim()) formData.append('horaCita', horaCita.trim());
  formData.append('archivo', archivo);

  const respuesta = await api.post('/postulaciones/publicas', formData);
  return respuesta.data;
}

export default api;