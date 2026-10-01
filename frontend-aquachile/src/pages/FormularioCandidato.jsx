import { useState } from 'react';
import axios from 'axios';

function FormularioCandidato() {
    // Estado para guardar los datos del formulario
    const [candidato, setCandidato] = useState({
        nombre: '',
        correo: '',
        telefono: '',
        cargoPostula: '',
        familiaCargo: '',
        origen: 'Externo', // Valor por defecto
        ubicacion: '',
        unidad: ''
    });

    const [mensaje, setMensaje] = useState(''); // Para mostrar alertas de éxito o error

    // Función para actualizar el estado cuando el usuario escribe
    const handleChange = (e) => {
        setCandidato({ ...candidato, [e.target.name]: e.target.value });
    };

    // Función que se ejecuta al presionar "Guardar"
    const handleSubmit = async (e) => {
        e.preventDefault(); // Evita que la página se recargue
        try {
            // Enviamos el objeto JSON al backend (Asegúrate de que Spring Boot esté corriendo)
            await axios.post('http://localhost:8080/api/candidatos', candidato);

            setMensaje('¡Candidato registrado con éxito en la Base de Datos!');

            // Limpiamos el formulario para ingresar otro
            setCandidato({
                nombre: '', correo: '', telefono: '', cargoPostula: '',
                familiaCargo: '', origen: 'Externo', ubicacion: '', unidad: ''
            });
        } catch (error) {
            console.error("Error al guardar:", error);
            setMensaje('Hubo un error al guardar. ¿Spring Boot está encendido?');
        }
    };

    return (
        <div className="p-6 max-w-4xl mx-auto">
            <h2 className="text-2xl font-bold mb-6 text-gray-800">Registrar Nuevo Candidato</h2>

            {/* Alerta de mensaje */}
            {mensaje && (
                <div className={`mb-4 p-3 rounded font-semibold ${mensaje.includes('éxito') ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'}`}>
                    {mensaje}
                </div>
            )}

            {/* Formulario con Tailwind CSS */}
            <form onSubmit={handleSubmit} className="bg-white p-6 rounded-lg shadow-md border grid grid-cols-1 md:grid-cols-2 gap-4">

                <div>
                    <label className="block text-gray-700 font-medium mb-1">Nombre Completo</label>
                    <input type="text" name="nombre" value={candidato.nombre} onChange={handleChange} required
                           className="w-full border border-gray-300 rounded p-2 focus:outline-none focus:border-blue-500" />
                </div>

                <div>
                    <label className="block text-gray-700 font-medium mb-1">Correo Electrónico</label>
                    <input type="email" name="correo" value={candidato.correo} onChange={handleChange} required
                           className="w-full border border-gray-300 rounded p-2 focus:outline-none focus:border-blue-500" />
                </div>

                <div>
                    <label className="block text-gray-700 font-medium mb-1">Teléfono</label>
                    <input type="text" name="telefono" value={candidato.telefono} onChange={handleChange}
                           className="w-full border border-gray-300 rounded p-2 focus:outline-none focus:border-blue-500" />
                </div>

                <div>
                    <label className="block text-gray-700 font-medium mb-1">Cargo al que postula</label>
                    <input type="text" name="cargoPostula" value={candidato.cargoPostula} onChange={handleChange} required
                           className="w-full border border-gray-300 rounded p-2 focus:outline-none focus:border-blue-500" />
                </div>

                <div>
                    <label className="block text-gray-700 font-medium mb-1">Familia de Cargo</label>
                    <input type="text" name="familiaCargo" value={candidato.familiaCargo} onChange={handleChange}
                           className="w-full border border-gray-300 rounded p-2 focus:outline-none focus:border-blue-500" />
                </div>

                <div>
                    <label className="block text-gray-700 font-medium mb-1">Origen del Candidato</label>
                    <select name="origen" value={candidato.origen} onChange={handleChange}
                            className="w-full border border-gray-300 rounded p-2 focus:outline-none focus:border-blue-500 bg-white">
                        <option value="Externo">Externo</option>
                        <option value="Interno">Interno</option>
                    </select>
                </div>

                <div>
                    <label className="block text-gray-700 font-medium mb-1">Ubicación</label>
                    <input type="text" name="ubicacion" value={candidato.ubicacion} onChange={handleChange}
                           className="w-full border border-gray-300 rounded p-2 focus:outline-none focus:border-blue-500" />
                </div>

                <div>
                    <label className="block text-gray-700 font-medium mb-1">Unidad</label>
                    <input type="text" name="unidad" value={candidato.unidad} onChange={handleChange}
                           className="w-full border border-gray-300 rounded p-2 focus:outline-none focus:border-blue-500" />
                </div>

                {/* Botón de envío que ocupa las dos columnas en pantallas grandes */}
                <div className="md:col-span-2 mt-4 flex justify-end">
                    <button type="submit" className="bg-blue-600 text-white font-bold py-2 px-6 rounded shadow hover:bg-blue-700 transition">
                        Guardar Candidato
                    </button>
                </div>
            </form>
        </div>
    );
}

export default FormularioCandidato;