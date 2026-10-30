import { useState } from 'react';
import { BrowserRouter, Link, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import RutaProtegida from './components/RutaProtegida';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import FormularioCandidato from './pages/FormularioCandidato';
import CandidatoDetalle from './pages/CandidatoDetalle';
import NuevaSolicitud from './pages/NuevaSolicitud';
import ListadoSolicitudes from './pages/ListadoSolicitudes';
import GestionarEvaluacion from './pages/GestionarEvaluacion';

function Navegacion() {
  const { usuario, cerrarSesion } = useAuth();
  const [cerrando, setCerrando] = useState(false);
  const [error, setError] = useState('');
  if (!usuario) return null;
  const analista = usuario.rol === 'ANALISTA';
  async function salir() {
    if (cerrando) return;
    setCerrando(true);setError('');
    try { await cerrarSesion(); }
    catch { setError('No se pudo cerrar sesión en el servidor. Inténtalo nuevamente.'); }
    finally {setCerrando(false);}
  }
  return <nav className="bg-blue-800 p-4 text-white shadow-md">
    <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-4">
      <span className="text-xl font-bold tracking-wider">AquaChile MVP</span>
      <div className="flex flex-wrap items-center gap-4 text-sm">
        {analista && <Link to="/" className="hover:underline">Dashboard</Link>}
        {analista && <Link to="/nuevo-candidato" className="hover:underline">Registrar candidato</Link>}
        <Link to="/solicitudes" className="hover:underline">Solicitudes</Link>
        <span className="rounded bg-blue-900 px-2 py-1 font-medium">{analista ? 'Analista' : 'Evaluador'}: {usuario.usuario}</span>
        <button type="button" onClick={salir} disabled={cerrando} className="rounded border border-white/60 px-3 py-1 hover:bg-blue-700 disabled:opacity-60">{cerrando?'Saliendo...':'Cerrar sesión'}</button>
      </div>
    </div>
    {error && <p role="alert" className="mx-auto mt-2 max-w-6xl text-sm text-yellow-100">{error}</p>}
  </nav>;
}

function AppRutas() {
  return <>
    <Navegacion />
    <main className="mx-auto max-w-7xl">
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route element={<RutaProtegida roles={['ANALISTA']} />}>
          <Route path="/" element={<Dashboard />} />
          <Route path="/nuevo-candidato" element={<FormularioCandidato />} />
          <Route path="/candidatos/:idCandidato" element={<CandidatoDetalle />} />
          <Route path="/nueva-solicitud/:idCandidato" element={<NuevaSolicitud />} />
        </Route>
        <Route element={<RutaProtegida roles={['ANALISTA', 'EVALUADOR']} />}>
          <Route path="/solicitudes" element={<ListadoSolicitudes />} />
        </Route>
        <Route element={<RutaProtegida roles={['EVALUADOR']} />}>
          <Route path="/evaluar/:idSolicitud" element={<GestionarEvaluacion />} />
        </Route>
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    </main>
  </>;
}

export default function App() {
  return <BrowserRouter><AuthProvider><AppRutas /></AuthProvider></BrowserRouter>;
}
