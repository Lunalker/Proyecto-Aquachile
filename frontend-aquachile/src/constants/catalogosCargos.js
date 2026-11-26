export const VALOR_OTRO = '__OTRO__';

export const CATALOGO_CARGOS = [
    {
        cargo: 'Analista de Sistemas',
        familiaCargo: 'Profesional B C',
        ubicacion: 'Planta Magallanes',
        unidad: 'Industrial',
    },
    {
        cargo: 'Asistente Bodega Planta',
        familiaCargo: 'Técnico B C',
        ubicacion: 'Pargua PA',
        unidad: 'Otros Producción',
    },
    {
        cargo: 'Coordinador servicios generales',
        familiaCargo: 'Profesional B C',
        ubicacion: 'Planta Magallanes',
        unidad: 'Industrial',
    },
    {
        cargo: 'Gruero',
        familiaCargo: 'Operario Calificado',
        ubicacion: 'Planta Calbuco',
        unidad: 'Industrial',
    },
    {
        cargo: 'Jefe Area Piscicultura',
        familiaCargo: 'Jefatura',
        ubicacion: 'Codinhue',
        unidad: 'Producción AD',
    },
    {
        cargo: 'Jefe de SSO',
        familiaCargo: 'Jefatura',
        ubicacion: 'Planta Cailin',
        unidad: 'Industrial',
    },
    {
        cargo: 'Líder Desarrollo Producción',
        familiaCargo: 'Profesional A',
        ubicacion: 'Oficina Central',
        unidad: 'Corporativo',
    },
    {
        cargo: 'Monitor Producción',
        familiaCargo: 'Técnico B C',
        ubicacion: 'Planta Calbuco',
        unidad: 'Industrial',
    },
    {
        cargo: 'Operador Sala Control',
        familiaCargo: 'Técnico B C',
        ubicacion: 'Pargua PA',
        unidad: 'Otros Producción',
    },
    {
        cargo: 'Supervisor Planta',
        familiaCargo: 'Supervisor B',
        ubicacion: 'Planta Magallanes',
        unidad: 'Industrial',
    },
    {
        cargo: 'Tecnico Mantencion Senior',
        familiaCargo: 'Técnico A',
        ubicacion: 'AM Melinka',
        unidad: 'Producción AM',
    },
];

export const FAMILIAS_CARGO_BASE = [
    'Profesional A',
    'Profesional B C',
    'Jefatura',
    'Supervisor B',
    'Técnico A',
    'Técnico B C',
    'Operario Calificado',
];

export const UBICACIONES_BASE = [
    'Oficina Central',
    'Planta Magallanes',
    'Planta Calbuco',
    'Planta Cailin',
    'Pargua PA',
    'AM Melinka',
    'Codinhue',
];

export const UNIDADES_BASE = [
    'Corporativo',
    'Industrial',
    'Otros Producción',
    'Producción AM',
    'Producción AD',
];

export function buscarInfoCargo(nombreCargo) {
    if (!nombreCargo) return null;
    const clave = nombreCargo.trim().toLowerCase();
    return CATALOGO_CARGOS.find((item) => item.cargo.toLowerCase() === clave) || null;
}

export function combinarOpcionesUnicas(listaBase, valoresExtra = []) {
    const vistos = new Set();
    const resultado = [];

    for (const item of [...listaBase, ...valoresExtra]) {
        if (!item || typeof item !== 'string') continue;
        const limpio = item.trim();
        if (!limpio) continue;
        const clave = limpio.toLowerCase();
        if (!vistos.has(clave)) {
            vistos.add(clave);
            resultado.push(limpio);
        }
    }
    return resultado;
}