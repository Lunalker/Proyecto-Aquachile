import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../services/api';
import EstadoBadge from '../components/EstadoBadge';
import TarjetaIndicador from '../components/TarjetaIndicador';
import Icono from '../components/Icono';
import { useAuth } from '../context/AuthContext';

const normalizar = valor => String(valor ?? '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();

export default function ListadoSolicitudes() {
  const { usuario } = useAuth();
  const evaluador = usuario?.rol === 'EVALUADOR';
  const [solicitudes, setSolicitudes] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  const [busqueda, setBusqueda] = useState('');
  const [filtroEstado, setFiltroEstado] = useState('Todos');
  const [filtroFecha, setFiltroFecha] = useState('');

  useEffect(() => {
    let activo = true;
    async function obtener() {
      try {
        const respuesta = await api.get('/solicitudes');
        if (!Array.isArray(respuesta.data)) throw new Error('Formato de datos incorrecto');
        if (activo) setSolicitudes(respuesta.data);
      } catch (err) {
        console.error('Error al consultar solicitudes:', err);
        if (activo) setError('No fue posible cargar las solicitudes. Comprueba la conexión con Spring Boot.');
      } finally { if (activo) setCargando(false); }
    }
    obtener();
    return () => { activo = false; };
  }, []);

  const filtradas = useMemo(() => {
    const texto = normalizar(busqueda);
    return solicitudes.filter(s => {
      const contenido = normalizar([s.candidato?.nombre, s.cargo, s.familiaCargo, s.profesionalResponsable].join(' '));
      return (!texto || contenido.includes(texto)) && (filtroEstado === 'Todos' || normalizar(s.estado) === normalizar(filtroEstado)) && (!filtroFecha || String(s.fechaSolicitud ?? '').slice(0,10) === filtroFecha);
    });
  }, [solicitudes, busqueda, filtroEstado, filtroFecha]);
  const contar = estado => solicitudes.filter(s => normalizar(s.estado) === normalizar(estado)).length;
  const valor = estado => cargando ? '...' : error ? '—' : contar(estado);
  const hayFiltros = Boolean(busqueda || filtroFecha || filtroEstado !== 'Todos');
  const limpiar = () => { setBusqueda(''); setFiltroEstado('Todos'); setFiltroFecha(''); };

  return <div className="page-container">
    <div className="page-heading">
      <div><p className="eyebrow">{evaluador ? 'ÁREA DE EVALUACIÓN' : 'SEGUIMIENTO'}</p><h1 className="page-title">{evaluador ? 'Bandeja de evaluaciones' : 'Solicitudes psicolaborales'}</h1><p className="page-description">{evaluador ? 'Consulta las solicitudes del sistema y gestiona sus evaluaciones.' : 'Revisa las solicitudes creadas y consulta el avance del proceso.'}</p></div>
      <div className="heading-actions">{!evaluador && <Link className="btn btn-primary" to="/"><Icono nombre="usuarios" size={16}/>Ver candidatos</Link>}</div>
    </div>

    <section className="kpi-grid" aria-label="Resumen de estados">
      <TarjetaIndicador titulo="Total solicitudes" valor={cargando ? '...' : error ? '—' : solicitudes.length} descripcion="Registradas en el sistema" color="azul"/>
      <TarjetaIndicador titulo="Pendientes" valor={valor('Pendiente')} descripcion="Aún sin finalizar" color="amarillo"/>
      <TarjetaIndicador titulo="En proceso" valor={valor('En proceso')} descripcion="Gestiones en curso" color="celeste"/>
      <TarjetaIndicador titulo="Finalizadas" valor={valor('Finalizada')} descripcion="Procesos completados" color="verde"/>
    </section>

    <section className="panel filter-panel" aria-label="Filtros de solicitudes">
      <div className="filter-heading"><Icono nombre="filtro" size={17}/>Filtrar solicitudes</div>
      <div className="filter-grid">
        <div className="field"><label htmlFor="buscar-solicitud">Buscar solicitud</label><input id="buscar-solicitud" type="search" value={busqueda} onChange={e=>setBusqueda(e.target.value)} placeholder="Escribe para buscar..."/></div>
        <div className="field"><label htmlFor="filtrar-estado">Estado</label><select id="filtrar-estado" value={filtroEstado} onChange={e=>setFiltroEstado(e.target.value)}><option value="Todos">Todos los estados</option><option value="Pendiente">Pendiente</option><option value="En proceso">En proceso</option><option value="Finalizada">Finalizada</option></select></div>
        <div className="field"><label htmlFor="filtrar-fecha">Fecha de solicitud</label><input id="filtrar-fecha" type="date" value={filtroFecha} onChange={e=>setFiltroFecha(e.target.value)}/></div>
      </div>
      <div className="filter-footer"><span aria-live="polite">{cargando ? 'Cargando solicitudes...' : error ? 'No se pudieron consultar los registros' : `${filtradas.length} de ${solicitudes.length} solicitudes`}</span><button className="btn btn-secondary btn-small" type="button" onClick={limpiar} disabled={!hayFiltros}>Limpiar filtros</button></div>
    </section>

    <section className="panel" aria-label="Listado de solicitudes">
      <div className="panel-heading"><div><h2>Registro de solicitudes</h2><p>Información de candidatos, responsables y estado actual</p></div><Icono nombre="clipboard" size={19} className="text-slate-400"/></div>
      {cargando ? <p className="empty-state" role="status">Cargando solicitudes...</p> : error ? <div role="alert" className="empty-state">{error}</div>
        : solicitudes.length === 0 ? <div className="empty-state"><span className="empty-icon"><Icono nombre="clipboard" size={23}/></span><strong>Aún no hay solicitudes</strong>Cuando se registren aparecerán aquí.</div>
        : filtradas.length === 0 ? <div className="empty-state"><span className="empty-icon"><Icono nombre="lupa" size={23}/></span><strong>No se encontraron coincidencias</strong><br/><button type="button" className="link-text mt-3" onClick={limpiar}>Mostrar todas las solicitudes</button></div>
        : <div className="table-scroll"><table className="data-table"><thead><tr><th>ID</th><th>Candidato</th><th>Cargo / Familia</th><th>Responsable</th><th>Fecha</th><th>Estado</th><th>Acción</th></tr></thead><tbody>
          {filtradas.map(s=><tr key={s.id}>
            <td className="table-id">#{s.id}</td>
            <td><div className="table-primary">{s.candidato?.nombre || 'Sin asignar'}</div></td>
            <td><div className="table-primary">{s.cargo || 'Sin cargo'}</div><div className="table-secondary">{s.familiaCargo || 'Sin familia'}</div></td>
            <td>{s.profesionalResponsable || 'Sin responsable'}</td>
            <td>{String(s.fechaSolicitud ?? '').slice(0,10) || 'Sin fecha'}</td>
            <td><EstadoBadge estado={s.estado}/></td>
            <td><div className="table-actions">
              {evaluador ? <Link className="btn btn-primary btn-small" to={`/evaluar/${s.id}`}>Gestionar<Icono nombre="flecha" size={14}/></Link> : <span className="table-readonly">Solo consulta</span>}
              {s.candidato?.id && <Link className="btn btn-secondary btn-small" to={`/documentos/${s.candidato.id}`} state={{nombre:s.candidato.nombre}}>Ver CV</Link>}
            </div></td>
          </tr>)}
        </tbody></table></div>}
    </section>
  </div>;
}
