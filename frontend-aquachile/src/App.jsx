
import { BrowserRouter as Router, Routes, Route, Link } from 'react-router-dom';
import Dashboard from './pages/Dashboard';
import FormularioCandidato from './pages/FormularioCandidato';
import NuevaSolicitud from './pages/NuevaSolicitud';
import ListadoSolicitudes from './pages/ListadoSolicitudes';
import GestionarEvaluacion from './pages/GestionarEvaluacion';


function App() {
    return (
        <Router>
            <div className="min-h-screen bg-gray-50">
                {/* Menú de Navegación (Navbar) */}
                <nav className="bg-blue-800 text-white p-4 shadow-md">
                    <div className="container mx-auto flex justify-between items-center">
                        <h1 className="text-xl font-bold tracking-wider">AquaChile MVP</h1>
                        <div className="space-x-4">
                            <Link to="/" className="hover:text-blue-300 transition">Dashboard</Link>
                            <Link to="/nuevo-candidato" className="hover:text-blue-300 transition">Registrar Candidato</Link>
                            <Link to="/solicitudes" className="hover:text-blue-300 transition">Panel de Evaluador</Link>
                        </div>
                    </div>
                </nav>

                {/* Contenedor principal donde cargan las páginas */}
                <main className="container mx-auto mt-6">
                    <Routes>
                        <Route path="/" element={<Dashboard />} />
                        <Route path="/nuevo-candidato" element={<FormularioCandidato />} />
                        <Route path="/nueva-solicitud/:idCandidato" element={<NuevaSolicitud />} />
                        <Route path="/solicitudes" element={<ListadoSolicitudes />} />
                        <Route path="/evaluar/:idSolicitud" element={<GestionarEvaluacion />} />
                    </Routes>
                </main>
            </div>
        </Router>
    );
}

export default App;