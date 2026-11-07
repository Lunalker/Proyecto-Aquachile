import Icono from './Icono';

const porColor = {
  azul: 'usuarios',
  amarillo: 'reloj',
  celeste: 'grafico',
  verde: 'check',
};

export default function TarjetaIndicador({ titulo, valor, descripcion, color = 'azul' }) {
  return <article className={`kpi-card ${color}`}>
    <div className="kpi-card-top"><span className="kpi-title">{titulo}</span><span className="kpi-icon"><Icono nombre={porColor[color] || 'panel'} size={19}/></span></div>
    <p className="kpi-value" aria-live="polite">{valor}</p>
    <p className="kpi-description">{descripcion}</p>
  </article>;
}
