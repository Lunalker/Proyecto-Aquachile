import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../services/api';
import TarjetaIndicador from '../components/TarjetaIndicador';
import EstadoBadge from '../components/EstadoBadge';
import Icono from '../components/Icono';

const normalizar = texto => String(texto ?? '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();

export default function Dashboard() {
  const [candidatos, setCandidatos] = useState([]);
  const [solicitudes, setSolicitudes] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [errorCandidatos, setErrorCandidatos] = useState('');
  const [errorSolicitudes, setErrorSolicitudes] = useState('');
  const [busqueda, setBusqueda] = useState('');

  useEffect(() => {
    let activo = true;
    async function cargarDatos() {
      const [resCandidatos, resSolicitudes] = await Promise.allSettled([
        api.get('/candidatos'), api.get('/solicitudes'),
      ]);
      if (!activo) return;
      if (resCandidatos.status === 'fulfilled' && Array.isArray(resCandidatos.value.data)) setCandidatos(resCandidatos.value.data);
      else setErrorCandidatos('No se pudo cargar el listado de candidatos.');
      if (resSolicitudes.status === 'fulfilled' && Array.isArray(resSolicitudes.value.data)) setSolicitudes(resSolicitudes.value.data);
      else setErrorSolicitudes('No se pudieron cargar los indicadores de solicitudes.');
      setCargando(false);
    }
    cargarDatos();
    return () => { activo = false; };
  }, []);

  const contar = estado => solicitudes.filter(s => String(s.estado ?? '').toLowerCase() === estado.toLowerCase()).length;
  const cantidad = estado => cargando ? '...' : errorSolicitudes ? '—' : contar(estado);
  const candidatosFiltrados = useMemo(() => candidatos.filter(c => normalizar([c.nombre, c.correo, c.cargoPostula, c.familiaCargo].join(' ')).includes(normalizar(busqueda))), [candidatos, busqueda]);
  const totalSolicitudes = solicitudes.length;
  const progreso = [
    { etiqueta: 'Pendientes', valor: contar('Pendiente'), color: 'amarillo' },
    { etiqueta: 'En proceso', valor: contar('En proceso'), color: 'celeste' },
    { etiqueta: 'Finalizadas', valor: contar('Finalizada'), color: 'verde' },
  ];

  return <div className="page-container">
    <div className="page-heading">
      <div><p className="eyebrow">VISIÓN GENERAL</p><h1 className="page-title">Panel de reclutamiento</h1><p className="page-description">Indicadores y gestión centralizada de evaluaciones psicolaborales.</p></div>
      <div className="heading-actions"><Link to="/solicitudes" className="btn btn-secondary"><Icono nombre="clipboard" size={16}/>Ver solicitudes</Link><Link to="/nuevo-candidato" className="btn btn-primary"><Icono nombre="mas" size={17}/>Nuevo candidato</Link></div>
    </div>

    <section className="welcome-banner" aria-label="Presentación del panel">
      <p className="banner-kicker"><Icono nombre="escudo" size={14}/> Gestión de personas</p>
      <h2>Todo el proceso, a la vista.</h2>
      <p>Consulta el estado de las solicitudes, revisa tus registros y da continuidad al proceso desde un mismo espacio.</p>
    </section>

    <section className="kpi-grid" aria-label="Indicadores de evaluaciones">
      <TarjetaIndicador titulo="Total candidatos" valor={cargando ? '...' : errorCandidatos ? '—' : candidatos.length} descripcion="Registrados en el sistema" color="azul"/>
      <TarjetaIndicador titulo="Pendientes" valor={cantidad('Pendiente')} descripcion="Solicitudes por gestionar" color="amarillo"/>
      <TarjetaIndicador titulo="En proceso" valor={cantidad('En proceso')} descripcion="Evaluaciones en curso" color="celeste"/>
      <TarjetaIndicador titulo="Finalizadas" valor={cantidad('Finalizada')} descripcion="Procesos completados" color="verde"/>
    </section>

    {(errorCandidatos || errorSolicitudes) && !cargando && <div role="alert" className="alert-box alert-box-error">{[errorCandidatos, errorSolicitudes].filter(Boolean).join(' ')} Verifica Spring Boot y MySQL.</div>}

    <div className="dashboard-two-col">
      <section className="panel" aria-label="Distribución de estados">
        <div className="panel-heading"><div><h2>Estado del proceso</h2><p>Distribución actual de las solicitudes</p></div><Icono nombre="grafico" size={20} className="text-slate-400"/></div>
        <div className="progress-block">
          {cargando ? <p className="text-sm text-slate-500" role="status">Cargando indicadores...</p>
            : errorSolicitudes ? <p className="text-sm text-slate-500">Información no disponible.</p>
            : totalSolicitudes === 0 ? <p className="text-sm text-slate-500">Todavía no hay solicitudes para mostrar.</p>
            : progreso.map(p => <div className="progress-row" key={p.etiqueta}>
              <span className="progress-label">{p.etiqueta}</span>
              <div className="progress-track" role="progressbar" aria-label={p.etiqueta} aria-valuemin={0} aria-valuemax={totalSolicitudes} aria-valuenow={p.valor}><span className={`progress-bar ${p.color}`} style={{width:`${p.valor / totalSolicitudes * 100}%`}}/></div>
              <span className="progress-count">{p.valor}</span>
            </div>)}
          {!cargando && !errorSolicitudes && <p className="text-xs mt-4 text-slate-400">Total de solicitudes: {totalSolicitudes}</p>}
        </div>
      </section>
      <section className="panel" aria-label="Accesos rápidos">
        <div className="panel-heading"><div><h2>Accesos rápidos</h2><p>Acciones frecuentes de reclutamiento</p></div></div>
        <div className="quick-actions">
          <Link to="/nuevo-candidato" className="quick-item"><span className="quick-item-icon"><Icono nombre="mas" size={19}/></span><span><strong>Registrar candidato</strong><small>Ingresar un nuevo postulante</small></span></Link>
          <Link to="/solicitudes" className="quick-item"><span className="quick-item-icon"><Icono nombre="clipboard" size={18}/></span><span><strong>Ver solicitudes</strong><small>Consultar cargos y estados</small></span></Link>
        </div>
      </section>
    </div>

    <section className="panel" aria-label="Listado de candidatos">
      <div className="panel-heading"><div><h2>Candidatos registrados</h2><p>Consulta la información principal y crea solicitudes</p></div><span className="text-xs text-slate-400">{errorCandidatos || cargando ? '—' : candidatos.length} registros</span></div>
      <div className="table-toolbar"><span className="toolbar-info">{cargando ? 'Cargando...' : errorCandidatos ? 'Información no disponible' : `${candidatosFiltrados.length} candidato(s)`}</span><input className="toolbar-search" type="search" aria-label="Buscar candidatos" placeholder="Buscar por nombre, cargo o correo" value={busqueda} onChange={e=>setBusqueda(e.target.value)}/></div>
      {cargando ? <p className="empty-state" role="status">Cargando candidatos...</p> : errorCandidatos ? <p role="alert" className="empty-state">No se pudo consultar la información.</p>
        : candidatos.length === 0 ? <div className="empty-state"><span className="empty-icon"><Icono nombre="usuarios" size={23}/></span><strong>No hay candidatos registrados</strong>Comienza registrando el primer candidato.</div>
        : candidatosFiltrados.length === 0 ? <div className="empty-state"><span className="empty-icon"><Icono nombre="lupa" size={23}/></span><strong>Sin coincidencias</strong>Prueba con otro nombre o cargo.</div>
        : <div className="table-scroll"><table className="data-table"><thead><tr><th>ID</th><th>Candidato</th><th>Cargo / familia</th><th>Origen</th><th>Acciones</th></tr></thead><tbody>
          {candidatosFiltrados.map(c=><tr key={c.id}>
            <td className="table-id">#{c.id}</td>
            <td><div className="table-primary">{c.nombre || 'Sin nombre'}</div><div className="table-secondary">{c.correo || 'Sin correo'}</div></td>
            <td><div className="table-primary">{c.cargoPostula || 'Sin cargo'}</div><div className="table-secondary">{c.familiaCargo || 'Sin familia'}</div></td>
            <td><span className={`status-badge ${String(c.origen).toLowerCase()==='interno'?'interno':'externo'}`}>{c.origen || 'Sin origen'}</span></td>
            <td><div className="table-actions"><Link className="btn btn-secondary btn-small" to={`/candidatos/${c.id}`}><Icono nombre="editar" size={14}/>Ver / Editar</Link><Link className="btn btn-soft btn-small" to={`/nueva-solicitud/${c.id}`}>Crear solicitud<Icono nombre="flecha" size={14}/></Link></div></td>
          </tr>)}
        </tbody></table></div>}
    </section>
  </div>;
}
