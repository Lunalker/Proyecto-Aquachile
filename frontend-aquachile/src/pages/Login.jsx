import { useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function Login() {
  const { usuario, cargando, iniciarSesion, errorConexion } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [nombre, setNombre] = useState('');
  const [clave, setClave] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState('');

  if (cargando) return <p className="p-10 text-center">Comprobando sesión...</p>;
  if (usuario) return <Navigate to={usuario.rol === 'EVALUADOR' ? '/solicitudes' : '/'} replace />;

  async function ingresar(e) {
    e.preventDefault();
    if (enviando) return;
    setEnviando(true); setError('');
    try {
      const cuenta = await iniciarSesion(nombre, clave);
      // Sólo permite el destino anterior si pertenece al rol autorizado.
      const desde = location.state?.desde;
      const destinoEvaluador = '/solicitudes';
      const permitido = cuenta.rol === 'EVALUADOR'
        ? (desde === '/solicitudes' || /^\/evaluar\/[0-9]+$/.test(desde || ''))
        : (desde === '/' || desde === '/nuevo-candidato' || /^\/candidatos\/[0-9]+$/.test(desde || '') || /^\/nueva-solicitud\/[0-9]+$/.test(desde || '') || desde === '/solicitudes');
      navigate(permitido ? desde : (cuenta.rol === 'EVALUADOR' ? destinoEvaluador : '/'), { replace: true });
    } catch (err) {
      setError(err.response?.status === 401
        ? 'Usuario o contraseña incorrectos.'
        : err.response?.status === 403
          ? 'El formulario de seguridad ha vencido. Recarga la página e inténtalo de nuevo.'
          : 'No fue posible iniciar sesión. Comprueba que Spring Boot esté funcionando.');
    } finally { setEnviando(false); }
  }

  return (
    <div className="min-h-[75vh] flex items-center justify-center bg-gray-50 px-4 py-8">
      <div className="w-full max-w-md rounded-xl border border-gray-200 bg-white p-7 shadow-md">
        <h1 className="mb-2 text-center text-3xl font-bold text-blue-800">AquaChile MVP</h1>
        <p className="mb-7 text-center text-gray-600">Acceso interno — Evaluaciones psicolaborales</p>
        {(error || errorConexion) && <p role="alert" className="mb-4 rounded bg-red-50 p-3 text-sm text-red-800">{error || errorConexion}</p>}
        <form onSubmit={ingresar} className="space-y-5">
          <div>
            <label htmlFor="usuario" className="mb-1 block font-semibold text-gray-700">Usuario</label>
            <input id="usuario" autoComplete="username" required value={nombre} onChange={e => setNombre(e.target.value)} disabled={enviando} className="w-full rounded border border-gray-300 p-3 focus:border-blue-500 focus:outline-none" />
          </div>
          <div>
            <label htmlFor="clave" className="mb-1 block font-semibold text-gray-700">Contraseña</label>
            <input id="clave" type="password" autoComplete="current-password" required value={clave} onChange={e => setClave(e.target.value)} disabled={enviando} className="w-full rounded border border-gray-300 p-3 focus:border-blue-500 focus:outline-none" />
          </div>
          <button type="submit" disabled={enviando} className="w-full rounded bg-blue-700 px-4 py-3 font-bold text-white hover:bg-blue-800 disabled:opacity-60">
            {enviando ? 'Verificando...' : 'Iniciar sesión'}
          </button>
        </form>
        <p className="mt-5 text-center text-xs text-gray-500">Usuarios ficticios para la demostración académica.</p>
      </div>
    </div>
  );
}
