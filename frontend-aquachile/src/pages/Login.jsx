import { useState } from 'react';

function Login({ onLogin }) {
    const [rolSeleccionado, setRolSeleccionado] = useState('Analista');

    const handleSubmit = (e) => {
        e.preventDefault();
        onLogin(rolSeleccionado);
    };

    return (
        <div className="min-h-screen flex items-center justify-center bg-gray-50 px-4">
            <div className="max-w-md w-full bg-white rounded-lg shadow-md p-8 border border-gray-200">
                <div className="text-center mb-8">
                    <h2 className="text-3xl font-bold text-blue-800 mb-2">AquaChile MVP</h2>
                    <p className="text-gray-500">Sistema de Evaluación Psicolaboral</p>
                </div>

                <form onSubmit={handleSubmit} className="space-y-6">
                    <div>
                        <label className="block text-gray-700 font-medium mb-2">Selecciona tu Rol de Ingreso</label>
                        <select
                            value={rolSeleccionado}
                            onChange={(e) => setRolSeleccionado(e.target.value)}
                            className="w-full border border-gray-300 rounded-lg p-3 focus:outline-none focus:border-blue-500 bg-white"
                        >
                            <option value="Analista">Analista de Reclutamiento</option>
                            <option value="Evaluador">Profesional Evaluador</option>
                        </select>
                    </div>

                    <button
                        type="submit"
                        className="w-full bg-blue-600 text-white font-bold py-3 px-4 rounded-lg shadow hover:bg-blue-700 transition"
                    >
                        Ingresar al Sistema
                    </button>
                </form>
            </div>
        </div>
    );
}

export default Login;