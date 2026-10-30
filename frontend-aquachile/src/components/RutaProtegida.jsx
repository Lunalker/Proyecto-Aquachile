import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function RutaProtegida({ roles }) {
  const { usuario, cargando } = useAuth();
  const location = useLocation();
  if (cargando) return <p role="status" className="p-8 text-center text-gray-600">Verificando sesión...</p>;
  if (!usuario) return <Navigate to="/login" state={{ desde: location.pathname }} replace />;
  if (roles && !roles.includes(usuario.rol)) {
    return <Navigate to={usuario.rol === 'EVALUADOR' ? '/solicitudes' : '/'} replace />;
  }
  return <Outlet />;
}
