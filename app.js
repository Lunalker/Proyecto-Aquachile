// State Management & LocalStorage Keys
const STORAGE_CANDIDATOS = 'aquachile_candidatos_v1';
const STORAGE_SOLICITUDES = 'aquachile_solicitudes_v1';

// Initial Mock Data Seeding
const initialCandidatos = [
    { id: '1', nombre: 'Juan Pablo Morales', correo: 'jp.morales@gmail.com', telefono: '+56 9 8765 4321', familia: 'Operaciones', cargo: 'Operario de Planta' },
    { id: '2', nombre: 'Camila Andrea Rojas', correo: 'crojas@empresa.cl', telefono: '+56 9 7654 3210', familia: 'Profesional/Técnico', cargo: 'Analista de Calidad' },
    { id: '3', nombre: 'Gonzalo Silva Tapia', correo: 'gsilva@empresa.com', telefono: '+56 9 6543 2109', familia: 'Jefaturas', cargo: 'Jefe de Centro de Cultivo' }
];

const initialSolicitudes = [
    {
        id: 'SOL-101',
        candidatoId: '1',
        candidatoNombre: 'Juan Pablo Morales',
        cargo: 'Operario de Planta',
        familia: 'Operaciones',
        fechaSolicitud: '2026-09-15',
        fechaEvaluacion: '2026-09-18',
        evaluador: 'Ps. Roberto Silva',
        estado: 'Finalizada',
        observaciones: 'Evaluación inicial aprobada.',
        apuntes: 'Candidato muestra alta disposición para turnos en planta.',
        resultado: 'Recomendado con adecuación al perfil operativo.'
    },
    {
        id: 'SOL-102',
        candidatoId: '2',
        candidatoNombre: 'Camila Andrea Rojas',
        cargo: 'Analista de Calidad',
        familia: 'Profesional/Técnico',
        fechaSolicitud: '2026-09-28',
        fechaEvaluacion: '',
        evaluador: 'Ps. Camila Henríquez',
        estado: 'En proceso',
        observaciones: 'Pendiente agendar entrevista técnica.',
        apuntes: 'Revisión de antecedentes en curso.',
        resultado: ''
    }
];

// App State
let candidatos = [];
let solicitudes = [];
let currentRole = 'Analista'; // 'Analista' | 'Psicólogo'
let chartEstadosInstance = null;
let chartFamiliasInstance = null;

// Initialize App
document.addEventListener('DOMContentLoaded', () => {
    loadData();
    renderDashboard();
    populateCandidatosDropdown();
});

function loadData() {
    const candData = localStorage.getItem(STORAGE_CANDIDATOS);
    const solData = localStorage.getItem(STORAGE_SOLICITUDES);

    if (!candData) {
        candidatos = [...initialCandidatos];
        saveCandidatos();
    } else {
        candidatos = JSON.parse(candData);
    }

    if (!solData) {
        solicitudes = [...initialSolicitudes];
        saveSolicitudes();
    } else {
        solicitudes = JSON.parse(solData);
    }
}

function saveCandidatos() {
    localStorage.setItem(STORAGE_CANDIDATOS, JSON.stringify(candidatos));
}

function saveSolicitudes() {
    localStorage.setItem(STORAGE_SOLICITUDES, JSON.stringify(solicitudes));
}

// Navigation Handler
function navigateTo(sectionId, event) {
    if (event) event.preventDefault();

    document.querySelectorAll('.content-section').forEach(sec => sec.classList.add('d-none'));
    document.querySelectorAll('.sidebar .nav-link').forEach(link => link.classList.remove('active'));

    const targetSection = document.getElementById(`section-${sectionId}`);
    if (targetSection) targetSection.classList.remove('d-none');

    if (event && event.currentTarget) {
        event.currentTarget.classList.add('active');
    }

    const titleMap = {
        'dashboard': 'Dashboard General',
        'candidatos': 'Gestión de Candidatos',
        'solicitudes': 'Solicitudes de Evaluación Psicolaboral',
        'evaluaciones': 'Gestión de Evaluaciones Psicolaborales',
        'automatizacion': 'Insumos / Visión de Automatización Copilot'
    };
    document.getElementById('pageTitle').innerText = titleMap[sectionId] || 'Gestión Psicolaboral';

    if (sectionId === 'dashboard') renderDashboard();
    if (sectionId === 'candidatos') renderCandidatos();
    if (sectionId === 'solicitudes') renderSolicitudes();
    if (sectionId === 'evaluaciones') renderEvaluaciones();
}

// Role Switcher
function toggleRole() {
    currentRole = currentRole === 'Analista' ? 'Psicólogo' : 'Analista';
    document.getElementById('currentRoleLabel').innerText = currentRole;
    showToast('Rol Actualizado', `Modo cambiado a: ${currentRole}`, 'info');
}

function toggleSidebar() {
    document.getElementById('sidebar').classList.toggle('show');
}

// Toast Helper
function showToast(title, message, type = 'success') {
    const toastEl = document.getElementById('appToast');
    document.getElementById('toastMessage').innerText = message;
    const toast = new bootstrap.Toast(toastEl);
    toast.show();
}

// Cargo Family Dropdown Dynamic Handler
const cargoCatalog = {
    'Operaciones': ['Operario de Planta', 'Técnico de Mantención', 'Asistente de Centro', 'Operador Logístico'],
    'Profesional/Técnico': ['Analista de Calidad', 'Ingeniero de Procesos', 'Prevencionista de Riesgos', 'Médico Veterinario'],
    'Jefaturas': ['Jefe de Centro de Cultivo', 'Jefe de Planta', 'Supervisor de Producción', 'Subgerente de Área']
};

function updateCargosByFamilia(familiaSelectId, cargoSelectId) {
    const familiaVal = document.getElementById(familiaSelectId).value;
    const cargoSelect = document.getElementById(cargoSelectId);
    cargoSelect.innerHTML = '<option value="">Seleccione cargo...</option>';

    if (familiaVal && cargoCatalog[familiaVal]) {
        cargoCatalog[familiaVal].forEach(cargo => {
            const opt = document.createElement('option');
            opt.value = cargo;
            opt.innerText = cargo;
            cargoSelect.appendChild(opt);
        });
    }
}

// CANDIDATOS MODULE
function renderCandidatos() {
    const search = document.getElementById('searchCandidato').value.toLowerCase();
    const filterFam = document.getElementById('filterFamiliaCandidato').value;
    const tbody = document.getElementById('tblCandidatosBody');
    tbody.innerHTML = '';

    const filtered = candidatos.filter(c => {
        const matchesSearch = c.nombre.toLowerCase().includes(search) ||
                              c.correo.toLowerCase().includes(search) ||
                              c.telefono.includes(search);
        const matchesFam = filterFam === '' || c.familia === filterFam;
        return matchesSearch && matchesFam;
    });

    if (filtered.length === 0) {
        tbody.innerHTML = `<tr><td colspan="6" class="text-center text-muted py-4">No se encontraron candidatos.</td></tr>`;
        return;
    }

    filtered.forEach(c => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td class="fw-semibold text-navy">${c.nombre}</td>
            <td>${c.correo}</td>
            <td>${c.telefono}</td>
            <td>${c.cargo}</td>
            <td><span class="badge bg-light text-dark border">${c.familia}</span></td>
            <td class="text-end">
                <button class="btn btn-sm btn-outline-secondary me-1" onclick="editCandidato('${c.id}')"><i class="fa-solid fa-pen"></i></button>
                <button class="btn btn-sm btn-outline-danger" onclick="deleteCandidato('${c.id}')"><i class="fa-solid fa-trash"></i></button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

function openModalCandidato() {
    document.getElementById('formCandidato').reset();
    document.getElementById('candId').value = '';
    document.getElementById('modalCandidatoTitle').innerText = 'Registrar Candidato';
    new bootstrap.Modal(document.getElementById('modalCandidato')).show();
}

function saveCandidato(e) {
    e.preventDefault();
    const id = document.getElementById('candId').value;
    const nombre = document.getElementById('candNombre').value;
    const correo = document.getElementById('candCorreo').value;
    const telefono = document.getElementById('candTelefono').value;
    const familia = document.getElementById('candFamilia').value;
    const cargo = document.getElementById('candCargo').value;

    if (id) {
        const idx = candidatos.findIndex(c => c.id === id);
        if (idx !== -1) {
            candidatos[idx] = { id, nombre, correo, telefono, familia, cargo };
            showToast('Candidato Actualizado', 'Los datos del candidato fueron modificados.');
        }
    } else {
        const newCand = {
            id: Date.now().toString(),
            nombre, correo, telefono, familia, cargo
        };
        candidatos.push(newCand);
        showToast('Candidato Creado', 'Nuevo candidato registrado con éxito.');
    }

    saveCandidatos();
    populateCandidatosDropdown();
    bootstrap.Modal.getInstance(document.getElementById('modalCandidato')).hide();
    renderCandidatos();
}

function editCandidato(id) {
    const c = candidatos.find(item => item.id === id);
    if (!c) return;

    document.getElementById('candId').value = c.id;
    document.getElementById('candNombre').value = c.nombre;
    document.getElementById('candCorreo').value = c.correo;
    document.getElementById('candTelefono').value = c.telefono;
    document.getElementById('candFamilia').value = c.familia;
    updateCargosByFamilia('candFamilia', 'candCargo');
    document.getElementById('candCargo').value = c.cargo;

    document.getElementById('modalCandidatoTitle').innerText = 'Editar Candidato';
    new bootstrap.Modal(document.getElementById('modalCandidato')).show();
}

function deleteCandidato(id) {
    if (confirm('¿Está seguro de eliminar este candidato?')) {
        candidatos = candidatos.filter(c => c.id !== id);
        saveCandidatos();
        populateCandidatosDropdown();
        renderCandidatos();
        showToast('Candidato Eliminado', 'El registro ha sido removido.', 'warning');
    }
}

// SOLICITUDES MODULE
function populateCandidatosDropdown() {
    const select = document.getElementById('solCandidatoId');
    select.innerHTML = '<option value="">Seleccione candidato...</option>';
    candidatos.forEach(c => {
        const opt = document.createElement('option');
        opt.value = c.id;
        opt.innerText = `${c.nombre} (${c.cargo})`;
        select.appendChild(opt);
    });
}

function onSolicitudCandidatoChange() {
    const candId = document.getElementById('solCandidatoId').value;
    const cand = candidatos.find(c => c.id === candId);
    if (cand) {
        document.getElementById('solFamiliaDisplay').value = cand.familia;
        document.getElementById('solCargoDisplay').value = cand.cargo;
    } else {
        document.getElementById('solFamiliaDisplay').value = '';
        document.getElementById('solCargoDisplay').value = '';
    }
}

function renderSolicitudes() {
    const search = document.getElementById('searchSolicitud').value.toLowerCase();
    const filterEst = document.getElementById('filterEstadoSolicitud').value;
    const tbody = document.getElementById('tblSolicitudesBody');
    tbody.innerHTML = '';

    const filtered = solicitudes.filter(s => {
        const matchesSearch = s.id.toLowerCase().includes(search) ||
                              s.candidatoNombre.toLowerCase().includes(search) ||
                              s.cargo.toLowerCase().includes(search);
        const matchesEstado = filterEst === '' || s.estado === filterEst;
        return matchesSearch && matchesEstado;
    });

    if (filtered.length === 0) {
        tbody.innerHTML = `<tr><td colspan="7" class="text-center text-muted py-4">No se encontraron solicitudes.</td></tr>`;
        return;
    }

    filtered.forEach(s => {
        let badgeClass = 'badge-pendiente';
        if (s.estado === 'En proceso') badgeClass = 'badge-proceso';
        if (s.estado === 'Finalizada') badgeClass = 'badge-finalizada';

        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td class="fw-bold text-navy">${s.id}</td>
            <td>${s.candidatoNombre}</td>
            <td><div>${s.cargo}</div><small class="text-muted">${s.familia}</small></td>
            <td>${s.fechaSolicitud}</td>
            <td>${s.evaluador}</td>
            <td><span class="badge-status ${badgeClass}">${s.estado}</span></td>
            <td class="text-end">
                <button class="btn btn-sm btn-navy" onclick="openModalEvaluacion('${s.id}')">
                    <i class="fa-solid fa-stethoscope me-1"></i> Gestionar
                </button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

function openModalSolicitud() {
    populateCandidatosDropdown();
    document.getElementById('formSolicitud').reset();
    document.getElementById('solFamiliaDisplay').value = '';
    document.getElementById('solCargoDisplay').value = '';
    new bootstrap.Modal(document.getElementById('modalSolicitud')).show();
}

function saveSolicitud(e) {
    e.preventDefault();
    const candId = document.getElementById('solCandidatoId').value;
    const cand = candidatos.find(c => c.id === candId);
    if (!cand) return;

    const newSol = {
        id: 'SOL-' + Math.floor(100 + Math.random() * 900),
        candidatoId: cand.id,
        candidatoNombre: cand.nombre,
        cargo: cand.cargo,
        familia: cand.familia,
        fechaSolicitud: new Date().toISOString().split('T')[0],
        fechaEvaluacion: '',
        evaluador: document.getElementById('solEvaluador').value,
        estado: 'Pendiente',
        observaciones: document.getElementById('solObservaciones').value,
        apuntes: '',
        resultado: ''
    };

    solicitudes.push(newSol);
    saveSolicitudes();
    bootstrap.Modal.getInstance(document.getElementById('modalSolicitud')).hide();
    renderSolicitudes();
    showToast('Solicitud Creada', `Solicitud ${newSol.id} generada exitosamente.`);
}

// EVALUACIONES MODULE
function renderEvaluaciones() {
    const tbody = document.getElementById('tblEvaluacionesBody');
    tbody.innerHTML = '';

    solicitudes.forEach(s => {
        let badgeClass = 'badge-pendiente';
        if (s.estado === 'En proceso') badgeClass = 'badge-proceso';
        if (s.estado === 'Finalizada') badgeClass = 'badge-finalizada';

        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td class="fw-semibold text-navy">${s.candidatoNombre}</td>
            <td>${s.cargo}</td>
            <td>${s.fechaEvaluacion || '<span class="text-muted">Por definir</span>'}</td>
            <td>${s.evaluador}</td>
            <td><span class="badge-status ${badgeClass}">${s.estado}</span></td>
            <td class="text-end">
                <button class="btn btn-sm btn-outline-teal" onclick="openModalEvaluacion('${s.id}')">
                    <i class="fa-solid fa-file-pen me-1"></i> Informe & Estado
                </button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

function openModalEvaluacion(solicitudId) {
    const s = solicitudes.find(item => item.id === solicitudId);
    if (!s) return;

    document.getElementById('evalSolicitudId').value = s.id;
    document.getElementById('evalCandNombre').innerText = s.candidatoNombre;
    document.getElementById('evalCandCargo').innerText = `${s.cargo} (${s.familia})`;
    document.getElementById('evalEvaluador').innerText = s.evaluador;
    document.getElementById('evalEstadoBadge').innerText = s.estado;
    
    document.getElementById('evalFecha').value = s.fechaEvaluacion || new Date().toISOString().split('T')[0];
    document.getElementById('evalEstado').value = s.estado;
    document.getElementById('evalApuntes').value = s.apuntes || '';
    document.getElementById('evalResultado').value = s.resultado || '';

    new bootstrap.Modal(document.getElementById('modalEvaluacion')).show();
}

function simularSintesisCopilot() {
    const apuntes = document.getElementById('evalApuntes').value;
    if (!apuntes.trim()) {
        showToast('Atención', 'Ingrese apuntes de entrevista para activar la síntesis.', 'warning');
        return;
    }

    document.getElementById('evalResultado').value = `[SÍNTESIS AUTOMÁTICA COPILOT]:
- Fortalezas destacadas: Alto compromiso con normas de bioseguridad y trabajo en equipo.
- Adecuación al perfil AquaChile: Ajuste adecuado para las exigencias de la familia de cargo.
- Recomendación final: Candidato RECOMENDADO para la posición.`;

    showToast('Copilot IA', 'Síntesis generada automáticamente a partir de los apuntes.');
}

function saveEvaluacion(e) {
    e.preventDefault();
    const solId = document.getElementById('evalSolicitudId').value;
    const idx = solicitudes.findIndex(s => s.id === solId);

    if (idx !== -1) {
        solicitudes[idx].fechaEvaluacion = document.getElementById('evalFecha').value;
        solicitudes[idx].estado = document.getElementById('evalEstado').value;
        solicitudes[idx].apuntes = document.getElementById('evalApuntes').value;
        solicitudes[idx].resultado = document.getElementById('evalResultado').value;

        saveSolicitudes();
        bootstrap.Modal.getInstance(document.getElementById('modalEvaluacion')).hide();
        renderSolicitudes();
        renderEvaluaciones();
        renderDashboard();
        showToast('Evaluación Guardada', 'Informe y estado actualizados correctamente.');
    }
}

// SIMULADOR DE INSUMOS
function actualizarSimuladorInsumos() {
    const fam = document.getElementById('selectFamiliaInsumo').value;
    document.getElementById('folderPath').innerText = `/OneDrive/Evaluaciones/${fam}/`;
    
    const list = document.getElementById('listInsumos');
    list.innerHTML = `
        <li class="list-group-item bg-transparent"><i class="fa-regular fa-file-excel text-success me-2"></i>Informe_Psicolaboral_${fam}.xlsx</li>
        <li class="list-group-item bg-transparent"><i class="fa-regular fa-file-word text-primary me-2"></i>Pauta_Entrevista_${fam}.docx</li>
        <li class="list-group-item bg-transparent"><i class="fa-regular fa-file-pdf text-danger me-2"></i>CV_Candidato.pdf</li>
    `;
}

// DASHBOARD CHARTS & STATS
function renderDashboard() {
    document.getElementById('statTotalCandidatos').innerText = candidatos.length;
    
    const pendientes = solicitudes.filter(s => s.estado === 'Pendiente').length;
    const enProceso = solicitudes.filter(s => s.estado === 'En proceso').length;
    const finalizadas = solicitudes.filter(s => s.estado === 'Finalizada').length;

    document.getElementById('statPendientes').innerText = pendientes;
    document.getElementById('statEnProceso').innerText = enProceso;
    document.getElementById('statFinalizadas').innerText = finalizadas;

    // Render Bar Chart
    const ctx1 = document.getElementById('chartEstados').getContext('2d');
    if (chartEstadosInstance) chartEstadosInstance.destroy();
    chartEstadosInstance = new Chart(ctx1, {
        type: 'bar',
        data: {
            labels: ['Pendiente', 'En proceso', 'Finalizada'],
            datasets: [{
                label: 'Cantidad de Solicitudes',
                data: [pendientes, enProceso, finalizadas],
                backgroundColor: ['#FEF3C7', '#E0F2FE', '#D1FAE5'],
                borderColor: ['#D97706', '#0284C7', '#059669'],
                borderWidth: 1.5,
                borderRadius: 6
            }]
        },
        options: {
            responsive: true,
            plugins: { legend: { display: false } },
            scales: { y: { beginAtZero: true, ticks: { stepSize: 1 } } }
        }
    });

    // Render Pie Chart
    const countFam = { 'Operaciones': 0, 'Profesional/Técnico': 0, 'Jefaturas': 0 };
    candidatos.forEach(c => { if (countFam[c.familia] !== undefined) countFam[c.familia]++; });

    const ctx2 = document.getElementById('chartFamilias').getContext('2d');
    if (chartFamiliasInstance) chartFamiliasInstance.destroy();
    chartFamiliasInstance = new Chart(ctx2, {
        type: 'doughnut',
        data: {
            labels: ['Operaciones', 'Profesional/Técnico', 'Jefaturas'],
            datasets: [{
                data: [countFam['Operaciones'], countFam['Profesional/Técnico'], countFam['Jefaturas']],
                backgroundColor: ['#0A2540', '#00A3A6', '#E76F51']
            }]
        },
        options: {
            responsive: true,
            plugins: { legend: { position: 'bottom' } }
        }
    });
}
