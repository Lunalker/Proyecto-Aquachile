import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import GestionarEvaluacion from '../GestionarEvaluacion';
import * as iaApi from '../../services/api';

vi.mock('../../services/api', async () => {
  const actual = await vi.importActual('../../services/api');
  return {
    ...actual,
    default: {
      post: vi.fn(),
      put: vi.fn(),
    },
    obtenerAnalisisValidadoSolicitud: vi.fn(),
    generarPreguntasApoyoSolicitud: vi.fn(),
  };
});

describe('GestionarEvaluacion con antecedentes validados y preguntas de apoyo', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('muestra antecedentes validados por el analista y genera preguntas de apoyo sin autocompletar la evaluación', async () => {
    const user = userEvent.setup();
    iaApi.obtenerAnalisisValidadoSolicitud.mockResolvedValue({
      analisisId: 10,
      nombreCandidatoInterno: 'Sofía Valenzuela',
      cargoPostula: 'Jefe de Centro',
      usuarioValidador: 'analista1',
      observacionesAnalista: 'Experiencia comprobada en centros de cultivo.',
      datosActuales: {
        tituloProfesional: 'Ingeniera Acuícola',
        resumenProfesionalObjetivo: 'Trayectoria en gestión productiva acuícola.',
        experienciasLaborales: [
          {
            cargo: 'Asistente de Centro',
            empresa: 'Acuícola Austral',
            periodo: '2021-2024',
            duracionCalculada: '3 años',
            evidencia: { pagina: 1, fragmento: 'Gestión operativa de centro de cultivo' },
          },
        ],
        formacionAcademica: [{ titulo: 'Ingeniería en Acuicultura', institucion: 'Universidad Austral' }],
        habilidadesTecnicas: [{ nombre: 'Control sanitario' }],
        certificacionesYLicencias: [],
        matrizRequisitos: [],
      },
    });

    iaApi.generarPreguntasApoyoSolicitud.mockResolvedValue({
      preguntas: [
        {
          pregunta: '¿Qué protocolos de bioseguridad aplicó en su gestión operativa?',
          objetivoVerificacion: 'Profundizar experiencia técnica',
          antecedenteRelacionado: 'Asistente de Centro',
        },
      ],
    });

    render(
        <MemoryRouter initialEntries={['/evaluar/7']}>
          <Routes>
            <Route path="/evaluar/:idSolicitud" element={<GestionarEvaluacion />} />
          </Routes>
        </MemoryRouter>
    );

    expect(await screen.findByText(/Ingeniera Acuícola/i)).toBeInTheDocument();
    expect(screen.getByText(/Experiencia comprobada en centros de cultivo/i)).toBeInTheDocument();

    const btnPreguntas = screen.getByRole('button', { name: /Generar preguntas sugeridas/i });
    await user.click(btnPreguntas);

    expect(await screen.findByText(/¿Qué protocolos de bioseguridad aplicó/i)).toBeInTheDocument();

    // Verifica que la IA NO autocompletará el campo obligatorio de resultado del evaluador
    const textareaResultado = screen.getByLabelText(/Resultado y observaciones psicolaborales/i);
    expect(textareaResultado).toHaveValue('');
  });
});