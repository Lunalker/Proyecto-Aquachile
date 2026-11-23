import { useEffect, useMemo, useState } from 'react';
import { enviarPostulacionPublica, listarOfertasPublicas } from './services/api';
import './App.css';

const LIMITE_BYTES = 5 * 1024 * 1024;
const EXTENSIONES_VALIDAS = /\.(pdf|doc|docx)$/i;

export default function App() {
  const [ofertas, setOfertas] = useState([]);
  const [cargandoOfertas, setCargandoOfertas] = useState(true);
  const [errorOfertas, setErrorOfertas] = useState('');

  // Filtros
  const [busqueda, setBusqueda] = useState('');
  const [categoriaFiltro, setCategoriaFiltro] = useState('TODAS');
  const [modalidadFiltro, setModalidadFiltro] = useState('TODAS');

  // Oferta seleccionada y formulario de postulación
  const [ofertaSeleccionada, setOfertaSeleccionada] = useState(null);
  const [nombre, setNombre] = useState('');
  const [correo, setCorreo] = useState('');
  const [telefono, setTelefono] = useState('');
  const [ubicacion, setUbicacion] = useState('');
  const [unidad, setUnidad] = useState('');
  const [archivo, setArchivo] = useState(null);

  const [enviando, setEnviando] = useState(false);
  const [errorEnvio, setErrorEnvio] = useState('');
  const [confirmacion, setConfirmacion] = useState(null);

  useEffect(() => {
    let activo = true;
    async function cargar() {
      setCargandoOfertas(true);
      setErrorOfertas('');
      try {
        const lista = await listarOfertasPublicas();
        if (!activo) return;
        const arr = Array.isArray(lista) ? lista : [];
        setOfertas(arr);
        if (arr.length > 0) {
          setOfertaSeleccionada(arr[0]);
        }
      } catch {
        if (activo) {
          setErrorOfertas('No fue posible cargar las ofertas laborales publicadas. Verifica que el backend esté activo.');
        }
      } finally {
        if (activo) setCargandoOfertas(false);
      }
    }
    cargar();
    return () => { activo = false; };
  }, []);

  const categorias = useMemo(() => {
    const unicas = new Set(ofertas.map(o => o.categoria).filter(Boolean));
    return ['TODAS', ...Array.from(unicas)];
  }, [ofertas]);

  const modalidades = useMemo(() => {
    const unicas = new Set(ofertas.map(o => o.modalidad).filter(Boolean));
    return ['TODAS', ...Array.from(unicas)];
  }, [ofertas]);

  const ofertasFiltradas = useMemo(() => {
    const q = busqueda.trim().toLowerCase();
    return ofertas.filter(o => {
      if (categoriaFiltro !== 'TODAS' && o.categoria !== categoriaFiltro) return false;
      if (modalidadFiltro !== 'TODAS' && o.modalidad !== modalidadFiltro) return false;
      if (!q) return true;
      return (
          (o.cargo || '').toLowerCase().includes(q) ||
          (o.categoria || '').toLowerCase().includes(q) ||
          (o.ubicacion || '').toLowerCase().includes(q) ||
          (o.descripcion || '').toLowerCase().includes(q)
      );
    });
  }, [ofertas, busqueda, categoriaFiltro, modalidadFiltro]);

  function seleccionarOferta(oferta) {
    setOfertaSeleccionada(oferta);
    setErrorEnvio('');
    setConfirmacion(null);
  }

  function manejarArchivo(e) {
    const file = e.target.files?.[0] || null;
    setArchivo(null);
    setErrorEnvio('');
    if (!file) return;

    if (!EXTENSIONES_VALIDAS.test(file.name)) {
      setErrorEnvio('Formato no válido. Adjunta un CV en PDF, DOC o DOCX.');
      e.target.value = '';
      return;
    }
    if (file.size === 0 || file.size > LIMITE_BYTES) {
      setErrorEnvio('El archivo CV debe pesar entre 1 byte y 5 MB.');
      e.target.value = '';
      return;
    }
    setArchivo(file);
  }

  async function manejarPostulacion(e) {
    e.preventDefault();
    if (!ofertaSeleccionada || enviando) return;
    if (!archivo) {
      setErrorEnvio('Debes adjuntar tu currículum en formato PDF, DOC o DOCX.');
      return;
    }

    setEnviando(true);
    setErrorEnvio('');
    setConfirmacion(null);

    try {
      const resultado = await enviarPostulacionPublica({
        ofertaId: ofertaSeleccionada.id,
        nombre,
        correo,
        telefono,
        ubicacion,
        unidad,
        archivo,
      });
      setConfirmacion(resultado);
      setNombre('');
      setCorreo('');
      setTelefono('');
      setUbicacion('');
      setUnidad('');
      setArchivo(null);
      e.target.reset();
    } catch (err) {
      const status = err.response?.status;
      const mensajeServidor = err.response?.data?.message || err.response?.data?.error;
      if (status === 409) {
        setErrorEnvio(mensajeServidor || 'Ya registraste una postulación previa para esta oferta laboral.');
      } else if (status === 413) {
        setErrorEnvio('El archivo supera el límite máximo permitido de 5 MB.');
      } else if (status === 400 || status === 404) {
        setErrorEnvio(mensajeServidor || 'Revisa los datos ingresados y verifica que el archivo CV sea auténtico.');
      } else {
        setErrorEnvio('Ocurrió un error al procesar tu postulación. Inténtalo nuevamente.');
      }
    } finally {
      setEnviando(false);
    }
  }

  return (
      <div style={{ maxWidth: '1160px', margin: '0 auto', padding: '1.5rem 1rem 3rem', fontFamily: 'system-ui, -apple-system, sans-serif', color: '#0f172a' }}>
        <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem', paddingBottom: '1.25rem', borderBottom: '1px solid #e2e8f0', marginBottom: '1.5rem' }}>
          <div>
          <span style={{ fontSize: '0.75rem', fontWeight: 700, letterSpacing: '0.08em', color: '#0f766e', textTransform: 'uppercase' }}>
            Portal de Postulantes · AquaChile
          </span>
            <h1 style={{ margin: '0.25rem 0', fontSize: '1.75rem' }}>Oportunidades Laborales Publicadas</h1>
            <p style={{ margin: 0, color: '#475569' }}>
              Selecciona una vacante disponible, revisa sus requisitos y envía tu currículum en un único paso seguro.
            </p>
          </div>
          <div style={{ padding: '0.55rem 0.9rem', borderRadius: '999px', background: '#ecfdf5', border: '1px solid #a7f3d0', color: '#065f46', fontSize: '0.85rem', fontWeight: 600 }}>
            Entorno académico · Datos ficticios
          </div>
        </header>

        {/* Filtros de búsqueda */}
        <section aria-label="Filtros de ofertas laborales" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '0.85rem', padding: '1rem', borderRadius: '12px', background: '#f8fafc', border: '1px solid #cbd5e1', marginBottom: '1.5rem' }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.3rem' }}>
            <label htmlFor="filtro-busqueda" style={{ fontSize: '0.85rem', fontWeight: 600 }}>Buscar cargo o ubicación</label>
            <input
                id="filtro-busqueda"
                type="search"
                value={busqueda}
                onChange={e => setBusqueda(e.target.value)}
                placeholder="Ej: Calbuco, Calidad, Cultivo..."
                style={{ padding: '0.55rem 0.75rem', borderRadius: '8px', border: '1px solid #cbd5e1' }}
            />
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.3rem' }}>
            <label htmlFor="filtro-categoria" style={{ fontSize: '0.85rem', fontWeight: 600 }}>Categoría</label>
            <select
                id="filtro-categoria"
                value={categoriaFiltro}
                onChange={e => setCategoriaFiltro(e.target.value)}
                style={{ padding: '0.55rem 0.75rem', borderRadius: '8px', border: '1px solid #cbd5e1', background: '#ffffff' }}
            >
              {categorias.map(cat => (
                  <option key={cat} value={cat}>{cat === 'TODAS' ? 'Todas las categorías' : cat}</option>
              ))}
            </select>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.3rem' }}>
            <label htmlFor="filtro-modalidad" style={{ fontSize: '0.85rem', fontWeight: 600 }}>Modalidad</label>
            <select
                id="filtro-modalidad"
                value={modalidadFiltro}
                onChange={e => setModalidadFiltro(e.target.value)}
                style={{ padding: '0.55rem 0.75rem', borderRadius: '8px', border: '1px solid #cbd5e1', background: '#ffffff' }}
            >
              {modalidades.map(mod => (
                  <option key={mod} value={mod}>{mod === 'TODAS' ? 'Todas las modalidades' : mod}</option>
              ))}
            </select>
          </div>
        </section>

        {errorOfertas && (
            <div role="alert" style={{ padding: '0.9rem', borderRadius: '8px', background: '#fef2f2', border: '1px solid #fecaca', color: '#991b1b', marginBottom: '1rem' }}>
              {errorOfertas}
            </div>
        )}

        {cargandoOfertas ? (
            <div role="status" style={{ padding: '2rem', textAlign: 'center', color: '#475569' }}>
              Cargando ofertas laborales disponibles...
            </div>
        ) : (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(340px, 1fr))', gap: '1.5rem', alignItems: 'start' }}>
              {/* Columna izquierda: listado de ofertas */}
              <section aria-label="Listado de ofertas publicadas" style={{ display: 'flex', flexDirection: 'column', gap: '0.85rem' }}>
                <h2 style={{ margin: 0, fontSize: '1.1rem' }}>
                  Vacantes disponibles ({ofertasFiltradas.length})
                </h2>
                {ofertasFiltradas.length === 0 ? (
                    <div style={{ padding: '1.25rem', borderRadius: '10px', border: '1px dashed #cbd5e1', background: '#ffffff' }}>
                      No se encontraron ofertas con los filtros seleccionados.
                    </div>
                ) : (
                    ofertasFiltradas.map(oferta => {
                      const activa = ofertaSeleccionada?.id === oferta.id;
                      return (
                          <article
                              key={oferta.id}
                              style={{
                                padding: '1rem',
                                borderRadius: '12px',
                                background: activa ? '#f0fdfa' : '#ffffff',
                                border: activa ? '2px solid #0f766e' : '1px solid #cbd5e1',
                                display: 'flex',
                                flexDirection: 'column',
                                gap: '0.5rem',
                              }}
                          >
                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '0.5rem' }}>
                              <div>
                        <span style={{ fontSize: '0.75rem', fontWeight: 700, padding: '0.15rem 0.55rem', borderRadius: '999px', background: '#e0f2fe', color: '#0369a1' }}>
                          {oferta.categoria}
                        </span>
                                <h3 style={{ margin: '0.4rem 0 0.15rem', fontSize: '1.08rem' }}>{oferta.cargo}</h3>
                                <small style={{ color: '#475569' }}>
                                  {oferta.ubicacion} · {oferta.modalidad} · Cupos: {oferta.cupos}
                                </small>
                              </div>
                              <button
                                  type="button"
                                  onClick={() => seleccionarOferta(oferta)}
                                  style={{
                                    padding: '0.45rem 0.85rem',
                                    borderRadius: '8px',
                                    border: 'none',
                                    background: activa ? '#0f766e' : '#0284c7',
                                    color: '#ffffff',
                                    fontWeight: 600,
                                    cursor: 'pointer',
                                  }}
                              >
                                {activa ? 'Seleccionada' : 'Postular'}
                              </button>
                            </div>
                            <p style={{ margin: 0, fontSize: '0.9rem', color: '#334155' }}>{oferta.descripcion}</p>
                            {oferta.rangoSalarial && (
                                <small style={{ fontWeight: 600, color: '#065f46' }}>Renta referencial: {oferta.rangoSalarial}</small>
                            )}
                          </article>
                      );
                    })
                )}
              </section>

              {/* Columna derecha: detalle de la oferta y formulario multipart único */}
              <section aria-label="Detalle de oferta y formulario de postulación" style={{ padding: '1.25rem', borderRadius: '12px', background: '#ffffff', border: '1px solid #cbd5e1' }}>
                {!ofertaSeleccionada ? (
                    <p>Selecciona una oferta laboral de la lista para ver sus detalles y postular.</p>
                ) : (
                    <>
                      <div style={{ borderBottom: '1px solid #e2e8f0', paddingBottom: '1rem', marginBottom: '1rem' }}>
                  <span style={{ fontSize: '0.75rem', fontWeight: 700, color: '#0f766e', textTransform: 'uppercase' }}>
                    {ofertaSeleccionada.categoria} · {ofertaSeleccionada.modalidad}
                  </span>
                        <h2 style={{ margin: '0.25rem 0' }}>{ofertaSeleccionada.cargo}</h2>
                        <p style={{ margin: '0 0 0.75rem', fontSize: '0.9rem', color: '#475569' }}>
                          Ubicación: <strong>{ofertaSeleccionada.ubicacion}</strong> · Cupos: <strong>{ofertaSeleccionada.cupos}</strong>
                          {ofertaSeleccionada.rangoSalarial ? ` · Rango: ${ofertaSeleccionada.rangoSalarial}` : ''}
                        </p>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.45rem', fontSize: '0.88rem', color: '#1e293b' }}>
                          <div><strong>Funciones:</strong> {ofertaSeleccionada.funciones}</div>
                          <div><strong>Requisitos:</strong> {ofertaSeleccionada.requisitos}</div>
                          {ofertaSeleccionada.beneficios && (
                              <div><strong>Beneficios:</strong> {ofertaSeleccionada.beneficios}</div>
                          )}
                        </div>
                      </div>

                      <h3 style={{ margin: '0 0 0.75rem' }}>Enviar postulación y CV</h3>

                      {errorEnvio && (
                          <div role="alert" style={{ padding: '0.8rem', borderRadius: '8px', background: '#fef2f2', border: '1px solid #fecaca', color: '#991b1b', marginBottom: '0.85rem', fontSize: '0.9rem' }}>
                            {errorEnvio}
                          </div>
                      )}

                      {confirmacion && (
                          <div role="status" style={{ padding: '0.9rem', borderRadius: '8px', background: '#ecfdf5', border: '1px solid #a7f3d0', color: '#065f46', marginBottom: '0.85rem', fontSize: '0.9rem' }}>
                            <strong>¡Postulación registrada correctamente!</strong>
                            <div>Postulación #{confirmacion.id} · Cargo: {confirmacion.ofertaCargo}</div>
                            <div>Contenedor CV asignado: <code>{confirmacion.contenedorUuid}</code></div>
                          </div>
                      )}

                      <form onSubmit={manejarPostulacion} style={{ display: 'flex', flexDirection: 'column', gap: '0.85rem' }}>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
                          <label htmlFor="postulante-nombre" style={{ fontSize: '0.85rem', fontWeight: 600 }}>Nombre completo *</label>
                          <input
                              id="postulante-nombre"
                              type="text"
                              required
                              minLength={3}
                              value={nombre}
                              onChange={e => setNombre(e.target.value)}
                              placeholder="Ej: Sofía Valenzuela Soto"
                              disabled={enviando}
                              style={{ padding: '0.55rem 0.75rem', borderRadius: '8px', border: '1px solid #cbd5e1' }}
                          />
                        </div>

                        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
                          <label htmlFor="postulante-correo" style={{ fontSize: '0.85rem', fontWeight: 600 }}>Correo electrónico *</label>
                          <input
                              id="postulante-correo"
                              type="email"
                              required
                              value={correo}
                              onChange={e => setCorreo(e.target.value)}
                              placeholder="ejemplo@correo.cl"
                              disabled={enviando}
                              style={{ padding: '0.55rem 0.75rem', borderRadius: '8px', border: '1px solid #cbd5e1' }}
                          />
                        </div>

                        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
                          <label htmlFor="postulante-telefono" style={{ fontSize: '0.85rem', fontWeight: 600 }}>Teléfono de contacto *</label>
                          <input
                              id="postulante-telefono"
                              type="tel"
                              required
                              minLength={6}
                              value={telefono}
                              onChange={e => setTelefono(e.target.value)}
                              placeholder="+56 9 1234 5678"
                              disabled={enviando}
                              style={{ padding: '0.55rem 0.75rem', borderRadius: '8px', border: '1px solid #cbd5e1' }}
                          />
                        </div>

                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.65rem' }}>
                          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
                            <label htmlFor="postulante-ubicacion" style={{ fontSize: '0.85rem', fontWeight: 600 }}>Comuna / Ciudad</label>
                            <input
                                id="postulante-ubicacion"
                                type="text"
                                value={ubicacion}
                                onChange={e => setUbicacion(e.target.value)}
                                placeholder="Ej: Puerto Montt"
                                disabled={enviando}
                                style={{ padding: '0.55rem 0.75rem', borderRadius: '8px', border: '1px solid #cbd5e1' }}
                            />
                          </div>
                          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
                            <label htmlFor="postulante-unidad" style={{ fontSize: '0.85rem', fontWeight: 600 }}>Área de interés</label>
                            <input
                                id="postulante-unidad"
                                type="text"
                                value={unidad}
                                onChange={e => setUnidad(e.target.value)}
                                placeholder="Ej: Operaciones"
                                disabled={enviando}
                                style={{ padding: '0.55rem 0.75rem', borderRadius: '8px', border: '1px solid #cbd5e1' }}
                            />
                          </div>
                        </div>

                        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
                          <label htmlFor="postulante-cv" style={{ fontSize: '0.85rem', fontWeight: 600 }}>Currículum Vitae (PDF, DOC o DOCX, máx. 5 MB) *</label>
                          <input
                              id="postulante-cv"
                              type="file"
                              required
                              accept=".pdf,.doc,.docx,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                              onChange={manejarArchivo}
                              disabled={enviando}
                          />
                          <small style={{ color: '#475569' }}>
                            {archivo ? `Archivo seleccionado: ${archivo.name} (${(archivo.size / 1024 / 1024).toFixed(2)} MB)` : 'Se valida la firma binaria real del documento en el servidor.'}
                          </small>
                        </div>

                        <button
                            type="submit"
                            disabled={enviando || !archivo}
                            style={{
                              padding: '0.75rem 1rem',
                              borderRadius: '8px',
                              border: 'none',
                              background: enviando || !archivo ? '#94a3b8' : '#0f766e',
                              color: '#ffffff',
                              fontWeight: 700,
                              fontSize: '0.95rem',
                              cursor: enviando || !archivo ? 'not-allowed' : 'pointer',
                            }}
                        >
                          {enviando ? 'Enviando postulación y CV...' : `Postular a ${ofertaSeleccionada.cargo}`}
                        </button>
                      </form>
                    </>
                )}
              </section>
            </div>
        )}
      </div>
  );
}