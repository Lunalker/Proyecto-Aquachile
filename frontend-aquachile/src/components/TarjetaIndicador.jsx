// Componente reutilizable: la vista decide el título, valor y color usando props.
export default function TarjetaIndicador({ titulo, valor, descripcion, color = 'azul' }) {
  const colores = {
    azul: 'border-blue-200 bg-blue-50',
    amarillo: 'border-yellow-200 bg-yellow-50',
    celeste: 'border-sky-200 bg-sky-50',
    verde: 'border-green-200 bg-green-50',
  };

  return (
    <article className={`rounded-xl border p-5 shadow-sm ${colores[color] || colores.azul}`}>
      <p className="text-sm font-medium text-gray-700">{titulo}</p>
      <p className="mt-2 text-3xl font-bold text-gray-900" aria-live="polite">
        {valor}
      </p>
      <p className="mt-2 text-xs text-gray-600">{descripcion}</p>
    </article>
  );
}
