import { useEffect, useState } from 'react';
import { BrowserRouter, NavLink, Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import RutaProtegida from './components/RutaProtegida';
import Icono from './components/Icono';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import FormularioCandidato from './pages/FormularioCandidato';
import CandidatoDetalle from './pages/CandidatoDetalle';
import NuevaSolicitud from './pages/NuevaSolicitud';
import ListadoSolicitudes from './pages/ListadoSolicitudes';
import GestionarEvaluacion from './pages/GestionarEvaluacion';
import DocumentosCandidato from './pages/DocumentosCandidato';

function Logo({ oscuro = false }) {
  return <div className={`brand ${oscuro ? 'brand-dark' : ''}`}>
    <span className="brand-mark"><Icono nombre="olas" size={23} /></span>
    <span className="brand-copy"><strong>AquaChile</strong><small>Gestión Psicolaboral</small></span>
  </div>;
}

function nombreSeccion(ruta) {
  if (ruta === '/') return 'Panel general';
  if (ruta === '/solicitudes') return 'Solicitudes';
  if (ruta === '/nuevo-candidato') return 'Nuevo candidato';
  if (ruta.startsWith('/candidatos/')) return 'Ficha de candidato';
  if (ruta.startsWith('/nueva-solicitud/')) return 'Nueva solicitud';
  if (ruta.startsWith('/evaluar/')) return 'Gestión de evaluación';
  if (ruta.startsWith('/documentos/')) return 'Documentos del candidato';
  return 'Panel de gestión';
}

function EntornoPrivado() {
  const { usuario, cerrarSesion } = useAuth();
  const location = useLocation();
  const [menuAbierto, setMenuAbierto] = useState(false);
  const [cerrando, setCerrando] = useState(false);
  const [error, setError] = useState('');
  const analista = usuario?.rol === 'ANALISTA';
  const rolNombre = analista ? 'Analista de reclutamiento' : 'Profesional evaluador';
  const links = analista ? [
    { to: '/', texto: 'Panel general', icono: 'panel', fin: true },
    { to: '/nuevo-candidato', texto: 'Registrar candidato', icono: 'usuarios' },
    { to: '/solicitudes', texto: 'Solicitudes', icono: 'clipboard' },
  ] : [
    { to: '/solicitudes', texto: 'Bandeja de evaluaciones', icono: 'clipboard' },
  ];

  useEffect(() => {
    if (!menuAbierto) return undefined;
    function cerrarConEscape(event) { if (event.key === 'Escape') setMenuAbierto(false); }
    window.addEventListener('keydown', cerrarConEscape);
    return () => window.removeEventListener('keydown', cerrarConEscape);
  }, [menuAbierto]);

  async function salir() {
    if (cerrando) return;
    setCerrando(true);
    setError('');
    try { await cerrarSesion(); }
    catch { setError('No se pudo cerrar la sesión. Inténtalo de nuevo.'); }
    finally { setCerrando(false); }
  }

  return <div className="app-shell">
    {menuAbierto && <button type="button" className="sidebar-overlay" aria-label="Cerrar menú de navegación" onClick={() => setMenuAbierto(false)} />}
    <aside className={`app-sidebar ${menuAbierto ? 'sidebar-open' : ''}`} aria-label="Navegación principal">
      <div className="sidebar-head">
        <Logo />
        <button type="button" className="sidebar-close" onClick={() => setMenuAbierto(false)} aria-label="Cerrar menú"><Icono nombre="cerrar" /></button>
      </div>
      <div className="sidebar-menu">
        <p className="sidebar-label">ESPACIO DE TRABAJO</p>
        <nav aria-label="Secciones del sistema">
          {links.map(({to,texto,icono,fin}) => <NavLink key={to} to={to} end={fin} onClick={() => setMenuAbierto(false)} className={({isActive}) => `sidebar-link ${isActive ? 'sidebar-link-active' : ''}`}>
            <Icono nombre={icono} size={19}/><span>{texto}</span>{to === '/solicitudes' && <Icono nombre="chevron" size={16} className="sidebar-chevron" />}
          </NavLink>)}
        </nav>
        <p className="sidebar-label sidebar-label-secondary">PERFIL ACTIVO</p>
        <div className="sidebar-profile">
          <span className="profile-avatar">{usuario?.usuario?.charAt(0).toUpperCase() || 'U'}</span>
          <span className="sidebar-profile-copy"><strong>{usuario?.usuario}</strong><small>{rolNombre}</small></span>
        </div>
      </div>
      <div className="sidebar-foot">
        <div className="privacy-note"><Icono nombre="escudo" size={17}/><span>Entorno académico seguro<br/><small>Solo datos ficticios</small></span></div>
        <button type="button" className="sidebar-logout" disabled={cerrando} onClick={salir}>
          <Icono nombre="salir" size={18}/>{cerrando ? 'Cerrando sesión...' : 'Cerrar sesión'}
        </button>
      </div>
    </aside>
    <div className="shell-content">
      <header className="topbar">
        <div className="topbar-left">
          <button type="button" className="mobile-menu-trigger" onClick={() => setMenuAbierto(true)} aria-label="Abrir menú" aria-expanded={menuAbierto}><Icono nombre="menu"/></button>
          <div className="topbar-breadcrumb"><span>Plataforma interna</span><Icono nombre="chevron" size={14}/><strong>{nombreSeccion(location.pathname)}</strong></div>
        </div>
        <div className="topbar-right">
          <span className="environment-pill"><span className="status-dot"/>MVP académico</span>
          <div className="topbar-user"><span className="topbar-avatar">{usuario?.usuario?.charAt(0).toUpperCase() || 'U'}</span><span><strong>{usuario?.usuario}</strong><small>{analista ? 'Analista' : 'Evaluador'}</small></span></div>
        </div>
      </header>
      {error && <div role="alert" className="global-alert">{error}</div>}
      <main id="contenido-principal" className="workspace-main"><Outlet/></main>
      <footer className="workspace-footer">AquaChile · Prototipo académico de gestión psicolaboral <span>Full Stack II · VcM</span></footer>
    </div>
  </div>;
}

function AppRutas() {
  const { usuario, cargando } = useAuth();
  if (cargando) return <div className="startup-screen"><span className="brand-mark"><Icono nombre="olas" size={26}/></span><p>Verificando sesión...</p></div>;
  return <Routes>
    <Route path="/login" element={<Login/>}/>
    <Route element={<RutaProtegida roles={['ANALISTA', 'EVALUADOR']} />}>
      <Route element={<EntornoPrivado/>}>
        <Route element={<RutaProtegida roles={['ANALISTA']} />}>
          <Route path="/" element={<Dashboard/>}/>
          <Route path="/nuevo-candidato" element={<FormularioCandidato/>}/>
          <Route path="/candidatos/:idCandidato" element={<CandidatoDetalle/>}/>
          <Route path="/nueva-solicitud/:idCandidato" element={<NuevaSolicitud/>}/>
        </Route>
        <Route path="/solicitudes" element={<ListadoSolicitudes/>}/>
        <Route path="/documentos/:idCandidato" element={<DocumentosCandidato/>}/>
        <Route element={<RutaProtegida roles={['EVALUADOR']} />}>
          <Route path="/evaluar/:idSolicitud" element={<GestionarEvaluacion/>}/>
        </Route>
      </Route>
    </Route>
    <Route path="*" element={<Navigate to={!usuario ? '/login' : usuario.rol === 'EVALUADOR' ? '/solicitudes' : '/'} replace/>}/>
  </Routes>;
}

export default function App() {
  return <BrowserRouter><AuthProvider><AppRutas/></AuthProvider></BrowserRouter>;
}
