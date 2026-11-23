package com.example.proyectoAquachile.Controller;

import com.example.proyectoAquachile.Model.RequisitoCargo;
import com.example.proyectoAquachile.Repository.RequisitoCargoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Permite al ANALISTA definir criterios profesionales por cargo contra los cuales
 * la IA contrastará los antecedentes encontrados en los currículums.
 */
@RestController
@RequestMapping("/api/requisitos-cargo")
public class RequisitoCargoController {

    private final RequisitoCargoRepository requisitos;

    public RequisitoCargoController(RequisitoCargoRepository requisitos) {
        this.requisitos = requisitos;
    }

    public record CrearRequisitoRequest(
            String cargo,
            String familiaCargo,
            String categoria,
            String nombreRequisito,
            String descripcion,
            Integer experienciaMinimaMeses,
            Boolean obligatorio
    ) {}

    public record PlantillaCargoRequest(
            String cargo,
            String familiaCargo
    ) {}

    @GetMapping
    @Transactional(readOnly = true)
    public List<RequisitoCargo> listar(@RequestParam(name = "cargo", required = false) String cargo) {
        if (cargo != null && !cargo.isBlank()) {
            return requisitos.findByCargoIgnoreCaseAndActivoTrueOrderByIdAsc(cargo.trim());
        }
        return requisitos.findByActivoTrueOrderByCargoAscIdAsc();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public RequisitoCargo crear(@RequestBody CrearRequisitoRequest req, Authentication auth) {
        if (req == null || req.cargo() == null || req.cargo().isBlank()
                || req.nombreRequisito() == null || req.nombreRequisito().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El cargo y el nombre del requisito son obligatorios.");
        }
        RequisitoCargo entidad = new RequisitoCargo();
        entidad.setCargo(req.cargo().trim());
        entidad.setFamiliaCargo(req.familiaCargo() != null ? req.familiaCargo().trim() : null);
        entidad.setCategoria(normalizarCategoria(req.categoria()));
        entidad.setNombreRequisito(req.nombreRequisito().trim());
        entidad.setDescripcion(req.descripcion() != null ? req.descripcion().trim() : null);
        entidad.setExperienciaMinimaMeses(
                req.experienciaMinimaMeses() != null && req.experienciaMinimaMeses() > 0
                        ? req.experienciaMinimaMeses()
                        : null
        );
        entidad.setObligatorio(req.obligatorio() == null || req.obligatorio());
        entidad.setActivo(true);
        entidad.setCreadoPor(auth != null ? auth.getName() : "analista");
        return requisitos.save(entidad);
    }

    @PostMapping("/plantilla")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public List<RequisitoCargo> cargarPlantillaBase(@RequestBody PlantillaCargoRequest req, Authentication auth) {
        if (req == null || req.cargo() == null || req.cargo().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debes indicar el cargo para cargar la plantilla.");
        }
        String cargoLimpio = req.cargo().trim();
        String familiaLimpia = req.familiaCargo() != null ? req.familiaCargo().trim() : null;
        String creador = auth != null ? auth.getName() : "analista";

        List<RequisitoCargo> existentes = requisitos.findByCargoIgnoreCaseAndActivoTrueOrderByIdAsc(cargoLimpio);
        Set<String> nombresExistentes = existentes.stream()
                .map(r -> r.getNombreRequisito().toLowerCase(Locale.ROOT).trim())
                .collect(Collectors.toSet());

        List<CrearRequisitoRequest> plantilla = generarPlantillaSegunCargo(cargoLimpio, familiaLimpia);
        for (CrearRequisitoRequest item : plantilla) {
            if (!nombresExistentes.contains(item.nombreRequisito().toLowerCase(Locale.ROOT).trim())) {
                RequisitoCargo nuevo = new RequisitoCargo();
                nuevo.setCargo(cargoLimpio);
                nuevo.setFamiliaCargo(familiaLimpia);
                nuevo.setCategoria(normalizarCategoria(item.categoria()));
                nuevo.setNombreRequisito(item.nombreRequisito());
                nuevo.setDescripcion(item.descripcion());
                nuevo.setExperienciaMinimaMeses(item.experienciaMinimaMeses());
                nuevo.setObligatorio(item.obligatorio() == null || item.obligatorio());
                nuevo.setActivo(true);
                nuevo.setCreadoPor(creador);
                requisitos.save(nuevo);
            }
        }
        return requisitos.findByCargoIgnoreCaseAndActivoTrueOrderByIdAsc(cargoLimpio);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void desactivar(@PathVariable Long id) {
        RequisitoCargo entidad = requisitos.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Requisito no encontrado."));
        entidad.setActivo(false);
        requisitos.save(entidad);
    }

    private String normalizarCategoria(String categoria) {
        if (categoria == null || categoria.isBlank()) return "EXPERIENCIA";
        String up = categoria.trim().toUpperCase(Locale.ROOT);
        return switch (up) {
            case "EXPERIENCIA", "FORMACION", "CERTIFICACION", "COMPETENCIA_TECNICA" -> up;
            default -> "EXPERIENCIA";
        };
    }

    private List<CrearRequisitoRequest> generarPlantillaSegunCargo(String cargo, String familia) {
        String lower = cargo.toLowerCase(Locale.ROOT);
        if (lower.contains("sala") || lower.contains("operador") || lower.contains("planta")) {
            return List.of(
                    new CrearRequisitoRequest(cargo, familia, "EXPERIENCIA",
                            "Experiencia operativa en monitoreo o sala de control",
                            "Acreditar operación continua de sistemas de control o procesos industriales.",
                            12, true),
                    new CrearRequisitoRequest(cargo, familia, "FORMACION",
                            "Título técnico o profesional en área industrial, acuícola o automatización",
                            "Formación técnica de nivel superior o ingeniería afín.",
                            null, true),
                    new CrearRequisitoRequest(cargo, familia, "CERTIFICACION",
                            "Normativa de seguridad industrial, inocuidad o prevención de riesgos",
                            "Conocimiento o certificación vigente en protocolos operativos y seguridad.",
                            null, true),
                    new CrearRequisitoRequest(cargo, familia, "COMPETENCIA_TECNICA",
                            "Manejo de sistemas SCADA, telemetría o registro de indicadores operativos",
                            "Uso práctico de herramientas digitales de monitoreo y reporte.",
                            null, false)
            );
        }
        return List.of(
                new CrearRequisitoRequest(cargo, familia, "EXPERIENCIA",
                        "Experiencia laboral previa en funciones equivalentes al cargo",
                        "Trayectoria demostrable con fechas explícitas en roles afines.",
                        12, true),
                new CrearRequisitoRequest(cargo, familia, "FORMACION",
                        "Formación técnica o profesional acorde al perfil del cargo",
                        "Título técnico o universitario relacionado con el área.",
                        null, true),
                new CrearRequisitoRequest(cargo, familia, "COMPETENCIA_TECNICA",
                        "Dominio de herramientas digitales, análisis de datos o gestión operativa",
                        "Manejo de Excel, ERP o software específico del área.",
                        null, false),
                new CrearRequisitoRequest(cargo, familia, "CERTIFICACION",
                        "Conocimiento de normativas de calidad, seguridad o estándares del sector",
                        "Cursos, licencias o certificaciones relevantes para AquaChile.",
                        null, false)
        );
    }
}