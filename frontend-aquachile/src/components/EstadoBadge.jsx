// Componente reutilizable para mostrar el estado de cada solicitud.
export default function EstadoBadge({ estado }) {
  const estilosPorEstado = {
    Pendiente: 'bg-yellow-100 text-yellow-800',
    'En proceso': 'bg-blue-100 text-blue-800',
    Finalizada: 'bg-green-100 text-green-800',
  };

  const texto = estado || 'Sin estado';
  const estilo = estilosPorEstado[texto] || 'bg-gray-100 text-gray-700';

  return (
    <span className={`inline-block rounded-full px-3 py-1 text-xs font-semibold ${estilo}`}>
      {texto}
    </span>
  );
}
