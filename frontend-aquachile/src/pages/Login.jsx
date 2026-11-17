import { useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import api from '../services/api';
import Icono from '../components/Icono';

export default function Login() {
  const { usuario, cargando, iniciarSesion, errorConexion } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [modo, setModo] = useState('login'); // 'login' | 'registro' | 'verificar'
  const [nombre, setNombre] = useState('');
  const [clave, setClave] = useState('');
  const [registro, setRegistro] = useState({
    nombreCompleto: '',
    correo: '',
    username: '',
    password: '',
    rol: 'ANALISTA',
  });
  const [correoVerificar, setCorreoVerificar] = useState('');
  const [codigo, setCodigo] = useState('');

  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState('');
  const [exito, setExito] = useState('');

  if (cargando) return <div className="startup-screen" role="status">Comprobando sesión...</div>;
  if (usuario) return <Navigate to={usuario.rol === 'EVALUADOR' ? '/solicitudes' : '/'} replace />;

  function cambiarModo(nuevoModo) {
    setModo(nuevoModo);
    setError('');
    setExito('');
  }

  const handleRegistroChange = (e) => {
    const { name, value } = e.target;
    setRegistro((prev) => ({ ...prev, [name]: value }));
  };

  async function ingresar(event) {
    event.preventDefault();
    if (enviando) return;
    setEnviando(true);
    setError('');
    setExito('');
    try {
      const cuenta = await iniciarSesion(nombre, clave);
      const desde = location.state?.desde;
      const permitido = cuenta.rol === 'EVALUADOR'
        ? (desde === '/solicitudes' || /^\/evaluar\/[0-9]+$/.test(desde || ''))
        : (desde === '/' || desde === '/nuevo-candidato' || /^\/candidatos\/[0-9]+$/.test(desde || '') || /^\/nueva-solicitud\/[0-9]+$/.test(desde || '') || desde === '/solicitudes');
      navigate(permitido ? desde : (cuenta.rol === 'EVALUADOR' ? '/solicitudes' : '/'), { replace: true });
    } catch (err) {
      setError(err.response?.status === 401
        ? 'Usuario o contraseña incorrectos (o cuenta aún no verificada).'
        : err.response?.status === 403
          ? 'La sesión de seguridad venció. Recarga e inténtalo nuevamente.'
          : 'No fue posible iniciar sesión. Comprueba que Spring Boot esté funcionando.');
    } finally {
      setEnviando(false);
    }
  }

  async function registrarUsuario(event) {
    event.preventDefault();
    if (enviando) return;
    setEnviando(true);
    setError('');
    setExito('');
    try {
      const correoLimpio = registro.correo.trim().toLowerCase();
      await api.post('/auth/registro', {
        ...registro,
        nombreCompleto: registro.nombreCompleto.trim(),
        correo: correoLimpio,
        username: registro.username.trim(),
      });
      setCorreoVerificar(correoLimpio);
      setNombre(registro.username.trim());
      setCodigo('');
      setExito(`Enviamos un código de 6 dígitos a ${correoLimpio}.`);
      setModo('verificar');
    } catch (err) {
      setError(
        err.response?.data?.error ||
        'No se pudo registrar el usuario. Verifica los datos y la conexión.'
      );
    } finally {
      setEnviando(false);
    }
  }

  async function confirmarCodigo(event) {
    event.preventDefault();
    if (enviando) return;
    setEnviando(true);
    setError('');
    setExito('');
    try {
      const res = await api.post('/auth/verificar-codigo', {
        correo: correoVerificar,
        codigo: codigo.trim(),
      });
      setNombre(res.data.username || nombre);
      setClave('');
      setCodigo('');
      setRegistro({
        nombreCompleto: '',
        correo: '',
        username: '',
        password: '',
        rol: 'ANALISTA',
      });
      setExito('Correo verificado correctamente. Ya puedes iniciar sesión.');
      setModo('login');
    } catch (err) {
      setError(err.response?.data?.error || 'Código inválido o expirado.');
    } finally {
      setEnviando(false);
    }
  }

  async function reenviarCodigo() {
    if (enviando) return;
    setEnviando(true);
    setError('');
    setExito('');
    try {
      const res = await api.post('/auth/reenviar-codigo', { correo: correoVerificar });
      setExito(res.data.mensaje || 'Nuevo código enviado.');
    } catch (err) {
      setError(err.response?.data?.error || 'No se pudo reenviar el código.');
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className="login-screen">
      <section className="login-brand-panel" aria-label="Presentación del sistema">
        <div className="brand">
          <span className="brand-mark"><Icono nombre="olas" size={26} /></span>
          <span className="brand-copy"><strong>AquaChile</strong><small>GESTIÓN PSICOLABORAL</small></span>
        </div>
        <div className="login-brand-content">
          <p className="login-kicker">PLATAFORMA INTERNA</p>
          <h1>Personas y procesos, en un solo lugar.</h1>
          <p>Centraliza candidatos, solicitudes y evaluaciones psicolaborales con una experiencia clara para los equipos de reclutamiento.</p>
          <div className="login-step-list">
            <div className="login-step"><span className="login-step-icon"><Icono nombre="usuarios" size={18} /></span>Registro de candidatos</div>
            <div className="login-step"><span className="login-step-icon"><Icono nombre="clipboard" size={18} /></span>Seguimiento de solicitudes</div>
            <div className="login-step"><span className="login-step-icon"><Icono nombre="grafico" size={18} /></span>Visibilidad del proceso</div>
          </div>
        </div>
        <p className="login-brand-foot">Proyecto académico VcM · Full Stack II</p>
      </section>

      <main className="login-form-side">
        <div className="login-form-wrapper">
          {modo === 'login' && (
            <>
              <p className="login-welcome">BIENVENIDO</p>
              <h2>Iniciar sesión</h2>
              <p className="login-form-subtitle">Ingresa con tu cuenta para acceder a las herramientas correspondientes a tu rol.</p>

              {(error || errorConexion) && (
                <div role="alert" className="alert-box alert-box-error">{error || errorConexion}</div>
              )}
              {exito && (
                <div role="status" className="alert-box alert-box-success">{exito}</div>
              )}

              <form className="login-form" onSubmit={ingresar}>
                <div className="field">
                  <label htmlFor="usuario">Usuario</label>
                  <input
                    id="usuario"
                    autoComplete="username"
                    autoCapitalize="none"
                    spellCheck={false}
                    placeholder="Ingresa tu usuario"
                    required
                    value={nombre}
                    disabled={enviando}
                    onChange={(e) => setNombre(e.target.value)}
                  />
                </div>
                <div className="field">
                  <label htmlFor="clave">Contraseña</label>
                  <input
                    id="clave"
                    type="password"
                    autoComplete="current-password"
                    placeholder="Ingresa tu contraseña"
                    required
                    value={clave}
                    disabled={enviando}
                    onChange={(e) => setClave(e.target.value)}
                  />
                </div>
                <button type="submit" className="login-submit" disabled={enviando}>
                  {enviando ? 'Verificando credenciales...' : 'Iniciar sesión'}
                  <Icono nombre="flecha" size={17} />
                </button>
              </form>

              <div className="mt-4">
                <button
                  type="button"
                  className="btn btn-secondary w-full"
                  disabled={enviando}
                  onClick={() => cambiarModo('registro')}
                >
                  <Icono nombre="usuarios" size={16} />
                  Registro
                </button>
              </div>
            </>
          )}

          {modo === 'registro' && (
            <>
              <p className="login-welcome">NUEVA CUENTA</p>
              <h2>Registro de usuario</h2>
              <p className="login-form-subtitle">Crea una cuenta de analista o evaluador. Te enviaremos un código a tu correo.</p>

              {error && (
                <div role="alert" className="alert-box alert-box-error">{error}</div>
              )}

              <form className="login-form" onSubmit={registrarUsuario}>
                <div className="field">
                  <label htmlFor="reg-rol">Rol del usuario</label>
                  <select
                    id="reg-rol"
                    name="rol"
                    value={registro.rol}
                    disabled={enviando}
                    onChange={handleRegistroChange}
                  >
                    <option value="ANALISTA">Analista de reclutamiento</option>
                    <option value="EVALUADOR">Profesional evaluador</option>
                  </select>
                </div>
                <div className="field">
                  <label htmlFor="reg-nombre">Nombre completo</label>
                  <input
                    id="reg-nombre"
                    name="nombreCompleto"
                    placeholder="Ej. Camila Soto"
                    required
                    value={registro.nombreCompleto}
                    disabled={enviando}
                    onChange={handleRegistroChange}
                  />
                </div>
                <div className="field">
                  <label htmlFor="reg-correo">Correo electrónico</label>
                  <input
                    id="reg-correo"
                    name="correo"
                    type="email"
                    placeholder="tu-correo@ejemplo.com"
                    required
                    value={registro.correo}
                    disabled={enviando}
                    onChange={handleRegistroChange}
                  />
                </div>
                <div className="field">
                  <label htmlFor="reg-username">Usuario</label>
                  <input
                    id="reg-username"
                    name="username"
                    autoCapitalize="none"
                    spellCheck={false}
                    placeholder="Nombre de usuario para ingresar"
                    required
                    value={registro.username}
                    disabled={enviando}
                    onChange={handleRegistroChange}
                  />
                </div>
                <div className="field">
                  <label htmlFor="reg-password">Contraseña</label>
                  <input
                    id="reg-password"
                    name="password"
                    type="password"
                    minLength={6}
                    placeholder="Mínimo 6 caracteres"
                    required
                    value={registro.password}
                    disabled={enviando}
                    onChange={handleRegistroChange}
                  />
                </div>
                <button type="submit" className="login-submit" disabled={enviando}>
                  {enviando ? 'Enviando código...' : 'Continuar y enviar código'}
                  <Icono nombre="flecha" size={17} />
                </button>
              </form>

              <div className="mt-4">
                <button
                  type="button"
                  className="btn btn-secondary w-full"
                  disabled={enviando}
                  onClick={() => cambiarModo('login')}
                >
                  <Icono nombre="flecha" size={16} className="rotate-180" />
                  Volver a iniciar sesión
                </button>
              </div>
            </>
          )}

          {modo === 'verificar' && (
            <>
              <p className="login-welcome">VERIFICACIÓN DE CORREO</p>
              <h2>Ingresa tu código</h2>
              <p className="login-form-subtitle">
                Escribe el código de 6 dígitos que enviamos a <strong>{correoVerificar}</strong>.
              </p>

              {error && (
                <div role="alert" className="alert-box alert-box-error">{error}</div>
              )}
              {exito && (
                <div role="status" className="alert-box alert-box-success">{exito}</div>
              )}

              <form className="login-form" onSubmit={confirmarCodigo}>
                <div className="field">
                  <label htmlFor="codigo-verificacion">Código de 6 dígitos</label>
                  <input
                    id="codigo-verificacion"
                    type="text"
                    inputMode="numeric"
                    pattern="[0-9]{6}"
                    maxLength={6}
                    placeholder="000000"
                    required
                    value={codigo}
                    disabled={enviando}
                    onChange={(e) => setCodigo(e.target.value.replace(/\D/g, '').slice(0, 6))}
                    style={{ textAlign: 'center', fontSize: '22px', letterSpacing: '8px', fontWeight: 700 }}
                  />
                </div>
                <button type="submit" className="login-submit" disabled={enviando || codigo.length !== 6}>
                  {enviando ? 'Verificando...' : 'Verificar cuenta'}
                  <Icono nombre="check" size={17} />
                </button>
              </form>

              <div className="mt-4 flex flex-col gap-2">
                <button
                  type="button"
                  className="btn btn-soft w-full"
                  disabled={enviando}
                  onClick={reenviarCodigo}
                >
                  Reenviar código
                </button>
                <button
                  type="button"
                  className="btn btn-secondary w-full"
                  disabled={enviando}
                  onClick={() => cambiarModo('registro')}
                >
                  Cambiar correo o datos
                </button>
              </div>
            </>
          )}

          <p className="login-footnote">
            <Icono nombre="escudo" size={15} className="inline-block mr-2 align-middle" />
            Acceso para usuarios autorizados. Este sistema es un prototipo académico que utiliza datos ficticios.
          </p>
        </div>
      </main>
    </div>
  );
}