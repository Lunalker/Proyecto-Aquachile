import axios from 'axios';

// Todas las solicitudes deben pasar por esta instancia (cookie de sesión + CSRF).
const api = axios.create({
    baseURL: 'http://localhost:8080/api',
    withCredentials: true,
    timeout: 45000,
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
    async (error) => {
        const config = error.config;
        const url = String(config?.url || '');
        if (error.response?.status === 401 && !url.startsWith('/auth/')) {
            window.dispatchEvent(new Event('aquachile:sesion-expirada'));
            return Promise.reject(error);
        }
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

// --- Servicios del módulo de análisis documental con IA ---

export const obtenerAnalisisCv = async (candidatoId) => {
    const respuesta = await api.get(`/documentos/candidatos/${candidatoId}/analisis`);
    return respuesta.status === 204 ? null : respuesta.data;
};

export const analizarCvCandidato = async (candidatoId, { forzar = false } = {}) => {
    const respuesta = await api.post(`/documentos/candidatos/${candidatoId}/analizar-ia`, null, {
        params: { forzar },
    });
    return respuesta.data;
};

export const guardarValidacionAnalisis = async (candidatoId, payload) => {
    const respuesta = await api.put(`/documentos/candidatos/${candidatoId}/validar`, payload);
    return respuesta.data;
};

export const obtenerAnalisisValidadoSolicitud = async (solicitudId) => {
    const respuesta = await api.get(`/documentos/solicitudes/${solicitudId}/analisis-validado`);
    return respuesta.status === 204 ? null : respuesta.data;
};

export const generarPreguntasApoyoSolicitud = async (solicitudId) => {
    const respuesta = await api.post(`/documentos/solicitudes/${solicitudId}/preguntas-apoyo`);
    return respuesta.data;
};

export const listarRequisitosCargo = async (cargo) => {
    const respuesta = await api.get('/requisitos-cargo', {
        params: cargo ? { cargo } : undefined,
    });
    return respuesta.data;
};

export const crearRequisitoCargo = async (payload) => {
    const respuesta = await api.post('/requisitos-cargo', payload);
    return respuesta.data;
};

export const eliminarRequisitoCargo = async (id) => {
    await api.delete(`/requisitos-cargo/${id}`);
};

export default api;