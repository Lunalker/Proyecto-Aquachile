export default function EstadoBadge({ estado }) {
  const nombre = String(estado || 'Sin estado');
  const clase = nombre === 'Pendiente' ? 'pendiente' : nombre === 'En proceso' ? 'en-proceso' : nombre === 'Finalizada' ? 'finalizada' : '';
  return <span className={`status-badge ${clase}`}>{nombre}</span>;
}
