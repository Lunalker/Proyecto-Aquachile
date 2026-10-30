import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import api from '../services/api';

export default function GestionarEvaluacion() {
  const {idSolicitud}=useParams();
  const navigate=useNavigate();
  const [evaluacion,setEvaluacion]=useState({resultado:'',estadoEvaluacion:'Recomendado'});
  const [estadoSolicitud,setEstadoSolicitud]=useState('Finalizada');
  const [guardando,setGuardando]=useState(false);
  const [error,setError]=useState('');
  const handleChange=e=>setEvaluacion(prev=>({...prev,[e.target.name]:e.target.value}));
  async function handleSubmit(e){
    e.preventDefault();if(guardando)return;
    setGuardando(true);setError('');
    try{
      // Mantenemos el flujo ya existente: crear evaluación y después actualizar estado.
      await api.post('/evaluaciones',{...evaluacion,solicitud:{id:Number(idSolicitud)}});
      await api.put(`/solicitudes/${idSolicitud}/estado`,estadoSolicitud,{headers:{'Content-Type':'text/plain'}});
      navigate('/solicitudes',{replace:true});
    }catch(err){setError(err.response?.status===403?'No tienes permisos para gestionar evaluaciones.':'No se pudo completar la operación. Si se creó la evaluación y falló el estado, evita guardarla de nuevo sin revisar.');}
    finally{setGuardando(false);}
  }
  return <div className="p-6 max-w-2xl mx-auto">
    <h2 className="text-2xl font-bold mb-6 text-gray-800">Gestionar Evaluación - Solicitud #{idSolicitud}</h2>
    {error&&<p role="alert" className="mb-4 rounded bg-red-50 p-3 text-red-700">{error}</p>}
    <form onSubmit={handleSubmit} className="bg-white p-6 rounded-lg shadow-md border flex flex-col gap-5">
      <div><label htmlFor="estadoSolicitud" className="block text-gray-700 font-medium mb-1">Actualizar Estado de la Solicitud</label>
        <select id="estadoSolicitud" value={estadoSolicitud} onChange={e=>setEstadoSolicitud(e.target.value)} className="w-full border border-gray-300 rounded p-2 bg-gray-50 font-semibold">
          <option value="Pendiente">Pendiente</option><option value="En proceso">En proceso</option><option value="Finalizada">Finalizada</option>
        </select>
      </div>
      <hr className="border-gray-200" />
      <div><label htmlFor="estadoEvaluacion" className="block text-gray-700 font-medium mb-1">Categoría del evaluado</label>
        <select id="estadoEvaluacion" name="estadoEvaluacion" value={evaluacion.estadoEvaluacion} onChange={handleChange} className="w-full border border-gray-300 rounded p-2 bg-white">
          <option value="Recomendado">Recomendado</option><option value="Recomendado con observaciones">Recomendado con observaciones</option><option value="No recomendado">No recomendado</option>
        </select>
      </div>
      <div><label htmlFor="resultado" className="block text-gray-700 font-medium mb-1">Resultado y Observaciones Psicolaborales</label>
        <textarea id="resultado" name="resultado" value={evaluacion.resultado} onChange={handleChange} rows="4" required placeholder="Registrar solo información ficticia para el MVP académico..." className="w-full border border-gray-300 rounded p-2" />
      </div>
      <div className="mt-4 flex justify-between items-center gap-3">
        <button type="button" onClick={()=>navigate('/solicitudes')} className="text-gray-500 hover:text-gray-800 font-medium">Volver sin guardar</button>
        <button type="submit" disabled={guardando} className="bg-indigo-600 text-white font-bold py-2 px-6 rounded shadow hover:bg-indigo-700 disabled:opacity-60">{guardando?'Guardando...':'Guardar Evaluación'}</button>
      </div>
    </form>
  </div>;
}
