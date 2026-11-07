import { useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import Icono from '../components/Icono';

export default function Login() {
  const { usuario, cargando, iniciarSesion, errorConexion } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [nombre, setNombre] = useState('');
  const [clave, setClave] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState('');

  if (cargando) return <div className="startup-screen" role="status">Comprobando sesión...</div>;
  if (usuario) return <Navigate to={usuario.rol === 'EVALUADOR' ? '/solicitudes' : '/'} replace/>;

  async function ingresar(event) {
    event.preventDefault();
    if (enviando) return;
    setEnviando(true);
    setError('');
    try {
      const cuenta = await iniciarSesion(nombre, clave);
      const desde = location.state?.desde;
      const permitido = cuenta.rol === 'EVALUADOR'
        ? (desde === '/solicitudes' || /^\/evaluar\/[0-9]+$/.test(desde || ''))
        : (desde === '/' || desde === '/nuevo-candidato' || /^\/candidatos\/[0-9]+$/.test(desde || '') || /^\/nueva-solicitud\/[0-9]+$/.test(desde || '') || desde === '/solicitudes');
      navigate(permitido ? desde : (cuenta.rol === 'EVALUADOR' ? '/solicitudes' : '/'), { replace: true });
    } catch (err) {
      setError(err.response?.status === 401
        ? 'Usuario o contraseña incorrectos.'
        : err.response?.status === 403
          ? 'La sesión de seguridad venció. Recarga e inténtalo nuevamente.'
          : 'No fue posible iniciar sesión. Comprueba que Spring Boot esté funcionando.');
    } finally { setEnviando(false); }
  }

  return <div className="login-screen">
    <section className="login-brand-panel" aria-label="Presentación del sistema">
      <div className="brand">
        <span className="brand-mark"><Icono nombre="olas" size={26}/></span>
        <span className="brand-copy"><strong>AquaChile</strong><small>GESTIÓN PSICOLABORAL</small></span>
      </div>
      <div className="login-brand-content">
        <p className="login-kicker">PLATAFORMA INTERNA</p>
        <h1>Personas y procesos, en un solo lugar.</h1>
        <p>Centraliza candidatos, solicitudes y evaluaciones psicolaborales con una experiencia clara para los equipos de reclutamiento.</p>
        <div className="login-step-list">
          <div className="login-step"><span className="login-step-icon"><Icono nombre="usuarios" size={18}/></span>Registro de candidatos</div>
          <div className="login-step"><span className="login-step-icon"><Icono nombre="clipboard" size={18}/></span>Seguimiento de solicitudes</div>
          <div className="login-step"><span className="login-step-icon"><Icono nombre="grafico" size={18}/></span>Visibilidad del proceso</div>
        </div>
      </div>
      <p className="login-brand-foot">Proyecto académico VcM · Full Stack II</p>
    </section>
    <main className="login-form-side">
      <div className="login-form-wrapper">
        <p className="login-welcome">BIENVENIDO</p>
        <h2>Iniciar sesión</h2>
        <p className="login-form-subtitle">Ingresa con tu cuenta para acceder a las herramientas correspondientes a tu rol.</p>
        {(error || errorConexion) && <div role="alert" className="alert-box alert-box-error">{error || errorConexion}</div>}
        <form className="login-form" onSubmit={ingresar}>
          <div className="field">
            <label htmlFor="usuario">Usuario</label>
            <input id="usuario" autoComplete="username" autoCapitalize="none" spellCheck={false} placeholder="Ingresa tu usuario" required value={nombre} disabled={enviando} onChange={e => setNombre(e.target.value)}/>
          </div>
          <div className="field">
            <label htmlFor="clave">Contraseña</label>
            <input id="clave" type="password" autoComplete="current-password" placeholder="Ingresa tu contraseña" required value={clave} disabled={enviando} onChange={e => setClave(e.target.value)}/>
          </div>
          <button type="submit" className="login-submit" disabled={enviando}>{enviando ? 'Verificando credenciales...' : 'Iniciar sesión'}<Icono nombre="flecha" size={17}/></button>
        </form>
        <p className="login-footnote"><Icono nombre="escudo" size={15} className="inline-block mr-2 align-middle"/>Acceso para usuarios autorizados. Este sistema es un prototipo académico que utiliza datos ficticios.</p>
      </div>
    </main>
  </div>;
}
