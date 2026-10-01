import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';

function NuevaSolicitud() {
    // useParams extrae el ID de la URL
    const { idCandidato } = useParams();
    const navigate = useNavigate();

    const [solicitud, setSolicitud] = useState({
        cargo: '',
        familiaCargo: '',
        profesionalResponsable: '',
        observaciones: ''
    });

    const handleChange = (e) => {
        setSolicitud({ ...solicitud, [e.target.name]: e.target.value });
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        try {
            // Spring Boot espera que le digamos a qué candidato pertenece esta solicitud.
            // Le pasamos el objeto anidado con el ID que sacamos de la URL.
            const payload = {
                ...solicitud,
                candidato: { id: parseInt(idCandidato) }
            };

            await axios.post('http://localhost:8080/api/solicitudes', payload);
            alert('¡Solicitud creada con éxito!');
            navigate('/'); // Nos devuelve al Dashboard
        } catch (error) {
            console.error("Error al crear solicitud:", error);
            alert('Hubo un error al guardar. Revisa la consola.');
        }
    };

    return (
        <div className="p-6 max-w-2xl mx-auto">
            <h2 className="text-2xl font-bold mb-6 text-gray-800">Crear Solicitud para Candidato #{idCandidato}</h2>

            <form onSubmit={handleSubmit} className="bg-white p-6 rounded-lg shadow-md border grid grid-cols-1 gap-4">
                <div>
                    <label className="block text-gray-700 font-medium mb-1">Cargo a Evaluar</label>
                    <input type="text" name="cargo" onChange={handleChange} required
                           className="w-full border border-gray-300 rounded p-2 focus:outline-none focus:border-blue-500" />
                </div>

                <div>
                    <label className="block text-gray-700 font-medium mb-1">Familia de Cargo</label>
                    <input type="text" name="familiaCargo" onChange={handleChange} required
                           className="w-full border border-gray-300 rounded p-2 focus:outline-none focus:border-blue-500" />
                </div>

                <div>
                    <label className="block text-gray-700 font-medium mb-1">Profesional Responsable (Evaluador)</label>
                    <input type="text" name="profesionalResponsable" onChange={handleChange} required
                           className="w-full border border-gray-300 rounded p-2 focus:outline-none focus:border-blue-500" />
                </div>

                <div>
                    <label className="block text-gray-700 font-medium mb-1">Observaciones Iniciales</label>
                    <textarea name="observaciones" onChange={handleChange} rows="3"
                              className="w-full border border-gray-300 rounded p-2 focus:outline-none focus:border-blue-500"></textarea>
                </div>

                <div className="mt-4 flex justify-between">
                    <button type="button" onClick={() => navigate('/')} className="text-gray-500 hover:text-gray-800 font-medium">
                        Cancelar
                    </button>
                    <button type="submit" className="bg-blue-600 text-white font-bold py-2 px-6 rounded shadow hover:bg-blue-700 transition">
                        Guardar Solicitud
                    </button>
                </div>
            </form>
        </div>
    );
}

export default NuevaSolicitud;