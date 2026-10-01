import { useState, useEffect } from 'react';
import axios from 'axios';
import { Link } from 'react-router-dom';

function Dashboard() {
    const [candidatos, setCandidatos] = useState([]);
    const [cargando, setCargando] = useState(true);

    // useEffect hace que la función se ejecute automáticamente al abrir la pantalla
    useEffect(() => {
        obtenerCandidatos();
    }, []);

    const obtenerCandidatos = async () => {
        try {
            // Hacemos la petición GET a tu API de Spring Boot
            const respuesta = await axios.get('http://localhost:8080/api/candidatos');
            setCandidatos(respuesta.data);
            setCargando(false);
        } catch (error) {
            console.error("Error al obtener los candidatos:", error);
            setCargando(false);
        }
    };

    return (
        <div className="p-6 max-w-6xl mx-auto">
            <div className="flex justify-between items-center mb-6">
                <h2 className="text-2xl font-bold text-gray-800">Candidatos Registrados</h2>
            </div>

            <div className="bg-white rounded-lg shadow border border-gray-200 overflow-hidden">
                {cargando ? (
                    <p className="p-6 text-gray-500 font-medium text-center">Cargando datos desde la base de datos...</p>
                ) : candidatos.length === 0 ? (
                    <p className="p-6 text-gray-500 font-medium text-center">Aún no hay candidatos registrados.</p>
                ) : (
                    <div className="overflow-x-auto">
                        <table className="w-full text-left border-collapse">
                            <thead>
                            <tr className="bg-blue-50 border-b border-gray-200 text-blue-800">
                                <th className="p-4 font-semibold">ID</th>
                                <th className="p-4 font-semibold">Candidato</th>
                                <th className="p-4 font-semibold">Cargo y Familia</th>
                                <th className="p-4 font-semibold">Origen</th>
                                <th className="p-4 font-semibold">Acciones</th>
                            </tr>
                            </thead>
                            <tbody>
                            {candidatos.map((candidato) => (
                                <tr key={candidato.id} className="border-b border-gray-100 hover:bg-gray-50 transition">
                                    <td className="p-4 text-gray-500 font-medium">#{candidato.id}</td>

                                    <td className="p-4">
                                        <p className="font-bold text-gray-800">{candidato.nombre}</p>
                                        <p className="text-sm text-gray-500">{candidato.correo}</p>
                                        <p className="text-xs text-gray-400">{candidato.telefono}</p>
                                    </td>

                                    <td className="p-4">
                                        <p className="font-medium text-gray-800">{candidato.cargoPostula}</p>
                                        <p className="text-sm text-gray-500">{candidato.familiaCargo}</p>
                                    </td>

                                    <td className="p-4">
                      <span className={`px-3 py-1 rounded-full text-xs font-bold ${
                          candidato.origen === 'Interno' ? 'bg-indigo-100 text-indigo-700' : 'bg-emerald-100 text-emerald-700'
                      }`}>
                        {candidato.origen}
                      </span>
                                    </td>

                                    <td className="p-4">
                                        <Link
                                            to={`/nueva-solicitud/${candidato.id}`}
                                            className="inline-block bg-blue-600 text-white px-4 py-2 rounded shadow hover:bg-blue-700 transition text-sm font-semibold"
                                        >
                                            Crear Solicitud
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

export default Dashboard;