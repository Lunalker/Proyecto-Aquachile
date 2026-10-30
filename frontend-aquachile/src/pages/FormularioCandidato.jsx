import { useState } from 'react';
import api from '../services/api';

const vacio = () => ({
  nombre: '', correo: '', telefono: '', cargoPostula: '',
  familiaCargo: '', origen: 'Externo', ubicacion: '', unidad: ''
});

export default function FormularioCandidato() {
  const [candidato, setCandidato] = useState(vacio);
  const [mensaje, setMensaje] = useState('');
  const [guardando, setGuardando] = useState(false);
  const handleChange = (e) => setCandidato(prev => ({...prev, [e.target.name]:e.target.value}));

  async function handleSubmit(e) {
    e.preventDefault();
    if (guardando) return;
    setMensaje(''); setGuardando(true);
    try {
      await api.post('/candidatos', candidato);
      setMensaje('¡Candidato registrado correctamente!');
      setCandidato(vacio());
    } catch (error) {
      setMensaje(error.response?.status === 403
        ? 'Tu usuario no tiene permiso para registrar candidatos.'
        : 'No se pudo guardar el candidato. Revisa los datos y la conexión.');
    } finally { setGuardando(false); }
  }

  const campos = [
    { name:'nombre',label:'Nombre completo', required:true },
    { name:'correo',label:'Correo electrónico',type:'email',required:true },
    { name:'telefono',label:'Teléfono',type:'tel' },
    { name:'cargoPostula',label:'Cargo al que postula',required:true },
    { name:'familiaCargo',label:'Familia de cargo' },
    { name:'ubicacion',label:'Ubicación' },
    { name:'unidad',label:'Unidad' },
  ];
  return <div className="p-6 max-w-4xl mx-auto">
    <h2 className="text-2xl font-bold mb-6 text-gray-800">Registrar Nuevo Candidato</h2>
    {mensaje && <div role="status" className={`mb-4 p-3 rounded font-semibold ${mensaje.includes('correctamente') ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'}`}>{mensaje}</div>}
    <form onSubmit={handleSubmit} className="bg-white p-6 rounded-lg shadow-md border grid grid-cols-1 md:grid-cols-2 gap-4">
      {campos.map(({name,label,type,required}) => <div key={name}>
        <label htmlFor={name} className="block text-gray-700 font-medium mb-1">{label}</label>
        <input id={name} type={type||'text'} name={name} value={candidato[name]} onChange={handleChange} required={Boolean(required)} disabled={guardando} className="w-full border border-gray-300 rounded p-2 focus:outline-none focus:border-blue-500" />
      </div>)}
      <div>
        <label htmlFor="origen" className="block text-gray-700 font-medium mb-1">Origen del Candidato</label>
        <select id="origen" name="origen" value={candidato.origen} onChange={handleChange} disabled={guardando} className="w-full border border-gray-300 rounded p-2 bg-white">
          <option value="Externo">Externo</option><option value="Interno">Interno</option>
        </select>
      </div>
      <div className="md:col-span-2 mt-4 flex justify-end">
        <button type="submit" disabled={guardando} className="bg-blue-600 text-white font-bold py-2 px-6 rounded shadow hover:bg-blue-700 disabled:opacity-60">{guardando?'Guardando...':'Guardar Candidato'}</button>
      </div>
    </form>
  </div>;
}
