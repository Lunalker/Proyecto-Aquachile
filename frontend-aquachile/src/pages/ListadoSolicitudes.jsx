import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import axios from 'axios';

function ListadoSolicitudes() {
    const [solicitudes, setSolicitudes] = useState([]);
    const [cargando, setCargando] = useState(true);

    useEffect(() => {
        obtenerSolicitudes();
    }, []);

    const obtenerSolicitudes = async () => {
        try {
            const respuesta = await axios.get('http://localhost:8080/api/solicitudes');
            setSolicitudes(respuesta.data);
            setCargando(false);
        } catch (error) {
            console.error("Error al obtener solicitudes:", error);
            setCargando(false);
        }
    };

    // Función para asignar colores según el estado de la solicitud
    const colorEstado = (estado) => {
        if (estado === 'Pendiente') return 'bg-yellow-100 text-yellow-800';
        if (estado === 'En proceso') return 'bg-blue-100 text-blue-800';
        if (estado === 'Finalizada') return 'bg-green-100 text-green-800';
        return 'bg-gray-100 text-gray-800';
    };

    return (
        <div className="p-6 max-w-6xl mx-auto">
            <h2 className="text-2xl font-bold mb-6 text-gray-800">Panel de Evaluación Psicolaboral</h2>

            <div className="bg-white rounded-lg shadow border border-gray-200 overflow-hidden">
                {cargando ? (
                    <p className="p-6 text-gray-500 text-center">Cargando solicitudes...</p>
                ) : solicitudes.length === 0 ? (
                    <p className="p-6 text-gray-500 text-center">No hay solicitudes pendientes.</p>
                ) : (
                    <div className="overflow-x-auto">
                        <table className="w-full text-left border-collapse">
                            <thead>
                            <tr className="bg-gray-50 border-b text-gray-700">
                                <th className="p-4 font-semibold">ID</th>
                                <th className="p-4 font-semibold">Candidato</th>
                                <th className="p-4 font-semibold">Cargo a Evaluar</th>
                                <th className="p-4 font-semibold">Responsable</th>
                                <th className="p-4 font-semibold">Estado</th>
                                <th className="p-4 font-semibold">Acción</th>
                            </tr>
                            </thead>
                            <tbody>
                            {solicitudes.map((solicitud) => (
                                <tr key={solicitud.id} className="border-b hover:bg-gray-50">
                                    <td className="p-4 text-gray-500">#{solicitud.id}</td>
                                    <td className="p-4 font-bold text-gray-800">
                                        {solicitud.candidato ? solicitud.candidato.nombre : 'Sin asignar'}
                                    </td>
                                    <td className="p-4 text-gray-600">{solicitud.cargo}</td>
                                    <td className="p-4 text-gray-600">{solicitud.profesionalResponsable}</td>
                                    <td className="p-4">
                      <span className={`px-3 py-1 rounded-full text-xs font-bold ${colorEstado(solicitud.estado)}`}>
                        {solicitud.estado}
                      </span>
                                    </td>
                                    <td className="p-4">
                                        {/* Este botón nos llevará luego al formulario de evaluación final */}
                                        <Link
                                            to={`/evaluar/${solicitud.id}`}
                                            className="bg-indigo-600 text-white px-4 py-2 rounded shadow hover:bg-indigo-700 transition text-sm font-semibold"
                                        >
                                            Gestionar
                                        </Link>
                                    </td>
                                </tr>
                            ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>
        </div>
    );
}

export default ListadoSolicitudes;