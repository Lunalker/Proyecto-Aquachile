import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';

function GestionarEvaluacion() {
    // Obtenemos el ID de la solicitud desde la URL
    const { idSolicitud } = useParams();
    const navigate = useNavigate();

    // Estado para la tabla "Evaluación"
    const [evaluacion, setEvaluacion] = useState({
        resultado: '',
        estadoEvaluacion: 'Recomendado'
    });

    // Estado para actualizar la tabla "Solicitud"
    const [estadoSolicitud, setEstadoSolicitud] = useState('Finalizada');

    const handleChange = (e) => {
        setEvaluacion({ ...evaluacion, [e.target.name]: e.target.value });
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        try {
            // 1. Guardar la nueva evaluación conectada al ID de la solicitud
            const payloadEvaluacion = {
                ...evaluacion,
                solicitud: { id: parseInt(idSolicitud) }
            };
            await axios.post('http://localhost:8080/api/evaluaciones', payloadEvaluacion);

            // 2. Actualizar el estado de la solicitud (Ej: pasar a "Finalizada")
            // Enviamos el texto plano tal como lo configuramos en el backend
            await axios.put(`http://localhost:8080/api/solicitudes/${idSolicitud}/estado`, estadoSolicitud, {
                headers: { 'Content-Type': 'text/plain' }
            });

            alert('¡Evaluación guardada y estado actualizado con éxito!');
            navigate('/solicitudes'); // Volvemos al panel
        } catch (error) {
            console.error("Error al gestionar:", error);
            alert('Hubo un error al guardar. Asegúrate de que Spring Boot siga corriendo.');
        }
    };

    return (
        <div className="p-6 max-w-2xl mx-auto">
            <h2 className="text-2xl font-bold mb-6 text-gray-800">Gestionar Evaluación - Solicitud #{idSolicitud}</h2>

            <form onSubmit={handleSubmit} className="bg-white p-6 rounded-lg shadow-md border flex flex-col gap-5">

                {/* Selector de Estado */}
                <div>
                    <label className="block text-gray-700 font-medium mb-1">Actualizar Estado de la Solicitud</label>
                    <select
                        value={estadoSolicitud}
                        onChange={(e) => setEstadoSolicitud(e.target.value)}
                        className="w-full border border-gray-300 rounded p-2 focus:outline-none focus:border-indigo-500 bg-gray-50 font-semibold"
                    >
                        <option value="Pendiente">Pendiente</option>
                        <option value="En proceso">En proceso</option>
                        <option value="Finalizada">Finalizada</option>
                    </select>
                </div>

                <hr className="border-gray-200" />

                {/* Datos de la Evaluación */}
                <div>
                    <label className="block text-gray-700 font-medium mb-1">Categoría del Evaluado</label>
                    <select
                        name="estadoEvaluacion"
                        value={evaluacion.estadoEvaluacion}
                        onChange={handleChange}
                        className="w-full border border-gray-300 rounded p-2 focus:outline-none focus:border-indigo-500 bg-white"
                    >
                        <option value="Recomendado">Recomendado</option>
                        <option value="Recomendado con observaciones">Recomendado con observaciones</option>
                        <option value="No recomendado">No recomendado</option>
                    </select>
                </div>

                <div>
                    <label className="block text-gray-700 font-medium mb-1">Resultado y Observaciones Psicolaborales</label>
                    <textarea
                        name="resultado"
                        value={evaluacion.resultado}
                        onChange={handleChange}
                        rows="4" required
                        placeholder="Escribe el informe detallado del candidato aquí..."
                        className="w-full border border-gray-300 rounded p-2 focus:outline-none focus:border-indigo-500"
                    ></textarea>
                </div>

                <div className="mt-4 flex justify-between items-center">
                    <button type="button" onClick={() => navigate('/solicitudes')} className="text-gray-500 hover:text-gray-800 font-medium">
                        Volver sin guardar
                    </button>
                    <button type="submit" className="bg-indigo-600 text-white font-bold py-2 px-6 rounded shadow hover:bg-indigo-700 transition">
                        Finalizar Evaluación
                    </button>
                </div>
            </form>
        </div>
    );
}

export default GestionarEvaluacion;