import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import DocumentosCandidato from '../DocumentosCandidato';
import api, * as iaApi from '../../services/api';

vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({ usuario: { username: 'analista1', rol: 'ANALISTA' } }),
}));

vi.mock('../../services/api', async () => {
  const actual = await vi.importActual('../../services/api');
  return {
    ...actual,
    default: {
      get: vi.fn(),
      post: vi.fn(),
      put: vi.fn(),
      delete: vi.fn(),
    },
    obtenerAnalisisCv: vi.fn(),
    analizarCvCandidato: vi.fn(),
    guardarValidacionAnalisis: vi.fn(),
    listarRequisitosCargo: vi.fn(),
    crearRequisitoCargo: vi.fn(),
    eliminarRequisitoCargo: vi.fn(),
  };
});

describe('DocumentosCandidato con análisis IA y revisión humana', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.get.mockImplementation((url) => {
      if (url.includes('/documentos/candidatos/')) {
        return Promise.resolve({
          data: {
            id: 5,
            candidatoId: 1,
            nombreArchivo: 'cv_sofia.pdf',
            tipoContenido: 'application/pdf',
            tamanoBytes: 120000,
            fechaCarga: '2026-11-18T12:00:00',
            analizableIa: true,
          },
        });
      }
      return Promise.resolve({
        data: { id: 1, nombre: 'Sofía Valenzuela', cargoPostula: 'Técnico en Mantención', familiaCargo: 'Operaciones' },
      });
    });
    iaApi.obtenerAnalisisCv.mockResolvedValue(null);
    iaApi.listarRequisitosCargo.mockResolvedValue([]);
  });

  it('permite analizar el CV, muestra evidencias verificables y valida antecedentes', async () => {
    const user = userEvent.setup();
    const respuestaAnalisis = {
      analisisId: 1,
      candidatoId: 1,
      cargoPostula: 'Técnico en Mantención',
      nombreArchivoAnalizado: 'cv_sofia.pdf',
      modeloUtilizado: 'llama-3.1-8b-instant',
      fechaGeneracion: '2026-11-18T12:05:00',
      estadoAnalisis: 'GENERADO',
      estadoRevision: 'PENDIENTE_REVISION',
      observacionesAnalista: '',
      datosActuales: {
        tituloProfesional: 'Técnico en Mantención Industrial',
        resumenProfesionalObjetivo: 'Experiencia en plantas industriales.',
        formacionAcademica: [],
        experienciasLaborales: [
          {
            cargo: 'Técnico Mantención',
            empresa: 'Empresa Sur',
            periodo: '2022-2024',
            duracionCalculada: '2 años',
            funciones: ['Mantenimiento preventivo'],
            evidencia: { pagina: 1, fragmento: 'Mantenimiento preventivo de equipos', estadoVerificacion: 'VERIFICADO_EN_DOCUMENTO' },
            estadoRevisionItem: 'PENDIENTE',
          },
        ],
        habilidadesTecnicas: [],
        competenciasDeclaradas: [],
        certificacionesYLicencias: [],
        matrizRequisitos: [],
        datosFaltantesOAmbiguos: [],
      },
    };

    iaApi.analizarCvCandidato.mockResolvedValue(respuestaAnalisis);
    iaApi.guardarValidacionAnalisis.mockResolvedValue({
      ...respuestaAnalisis,
      estadoRevision: 'VALIDADO',
      usuarioValidador: 'analista1',
    });

    render(
        <MemoryRouter initialEntries={['/documentos/1']}>
          <Routes>
            <Route path="/documentos/:idCandidato" element={<DocumentosCandidato />} />
          </Routes>
        </MemoryRouter>
    );

    const botonAnalizar = await screen.findByRole('button', { name: /Analizar CV con IA/i });
    await user.click(botonAnalizar);

    expect(await screen.findByText(/Mantenimiento preventivo de equipos/i)).toBeInTheDocument();

    const botonValidar = screen.getByRole('button', { name: /Validar antecedentes para evaluador/i });
    await user.click(botonValidar);

    await waitFor(() => {
      expect(iaApi.guardarValidacionAnalisis).toHaveBeenCalledWith('1', expect.objectContaining({ confirmarValidacion: true }));
    });
  });

  it('deshabilita el análisis IA cuando el documento adjunto es DOCX', async () => {
    api.get.mockImplementation((url) => {
      if (url.includes('/documentos/candidatos/')) {
        return Promise.resolve({
          data: {
            id: 6,
            candidatoId: 1,
            nombreArchivo: 'cv_word.docx',
            tipoContenido: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
            tamanoBytes: 85000,
            fechaCarga: '2026-11-18T12:00:00',
            analizableIa: false,
            motivoNoAnalizable: 'El análisis con IA admite únicamente documentos PDF auténticos.',
          },
        });
      }
      return Promise.resolve({ data: { id: 1, nombre: 'Sofía Valenzuela' } });
    });

    render(
        <MemoryRouter initialEntries={['/documentos/1']}>
          <Routes>
            <Route path="/documentos/:idCandidato" element={<DocumentosCandidato />} />
          </Routes>
        </MemoryRouter>
    );

    const botonAnalizar = await screen.findByRole('button', { name: /Analizar CV con IA/i });
    expect(botonAnalizar).toBeDisabled();
    expect(screen.getByText(/admite únicamente documentos PDF auténticos/i)).toBeInTheDocument();
  });
});