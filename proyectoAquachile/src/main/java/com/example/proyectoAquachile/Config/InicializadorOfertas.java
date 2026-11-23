package com.example.proyectoAquachile.Config;

import com.example.proyectoAquachile.Model.OfertaLaboral;
import com.example.proyectoAquachile.Repository.OfertaLaboralRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.List;

@Configuration
public class InicializadorOfertas {

    @Bean
    CommandLineRunner cargarOfertasIniciales(OfertaLaboralRepository ofertaRepository) {
        return args -> {
            if (ofertaRepository.count() > 0) {
                return;
            }

            OfertaLaboral o1 = new OfertaLaboral();
            o1.setCargo("Jefe de Centro de Cultivo");
            o1.setCategoria("Operaciones Mar");
            o1.setUbicacion("Calbuco, Región de Los Lagos");
            o1.setModalidad("Presencial / Turnos");
            o1.setFechaPublicacion(LocalDateTime.now().minusDays(2));
            o1.setCupos(2);
            o1.setDescripcion("Liderar la operación productiva, sanitaria y ambiental de centro de cultivo marino asegurando cumplimiento normativo y bienestar del equipo.");
            o1.setFunciones("Planificar alimentación y biomasa; supervisar indicadores sanitarios y ambientales; coordinar equipos operativos en pontón.");
            o1.setRequisitos("Título en Ingeniería en Acuicultura, Biología Marina o afín; al menos 3 años de experiencia liderando centros de cultivo de salmónidos.");
            o1.setBeneficios("Seguro complementario de salud, bono por desempeño productivo, traslado y alimentación en faena.");
            o1.setRangoSalarial("$2.400.000 - $2.900.000 CLP");
            o1.setEstadoPublicacion(OfertaLaboral.ESTADO_PUBLICADA);

            OfertaLaboral o2 = new OfertaLaboral();
            o2.setCargo("Analista de Control de Calidad");
            o2.setCategoria("Procesos y Planta");
            o2.setUbicacion("Puerto Montt, Región de Los Lagos");
            o2.setModalidad("Presencial");
            o2.setFechaPublicacion(LocalDateTime.now().minusDays(1));
            o2.setCupos(3);
            o2.setDescripcion("Asegurar el cumplimiento de estándares HACCP, BRC y certificaciones internacionales en planta de proceso primario y secundario.");
            o2.setFunciones("Auditar líneas de proceso; validar registros de trazabilidad y temperatura; gestionar acciones correctivas ante desviaciones.");
            o2.setRequisitos("Ingeniería en Alimentos, Control de Calidad o Técnico Superior afín; manejo de normativa HACCP y análisis microbiológico.");
            o2.setBeneficios("Casino corporativo, bus de acercamiento, seguro de salud y capacitación continua.");
            o2.setRangoSalarial("$1.350.000 - $1.650.000 CLP");
            o2.setEstadoPublicacion(OfertaLaboral.ESTADO_PUBLICADA);

            OfertaLaboral o3 = new OfertaLaboral();
            o3.setCargo("Especialista en Salud de Peces");
            o3.setCategoria("Salud y Medioambiente");
            o3.setUbicacion("Chiloé, Región de Los Lagos");
            o3.setModalidad("Híbrido / Terreno");
            o3.setFechaPublicacion(LocalDateTime.now());
            o3.setCupos(1);
            o3.setDescripcion("Monitorear el estado sanitario de los planteles y diseñar planes preventivos en coordinación con equipos de agua dulce y mar.");
            o3.setFunciones("Realizar necropsias y muestreos en terreno; analizar resultados PCR; emitir informes técnicos para autoridad sanitaria.");
            o3.setRequisitos("Médico Veterinario con experiencia comprobable en ictiopatología y normativa Sernapesca.");
            o3.setBeneficios("Vehículo asignado en terreno, seguro complementario, bono anual y desarrollo de carrera.");
            o3.setRangoSalarial("$2.100.000 - $2.500.000 CLP");
            o3.setEstadoPublicacion(OfertaLaboral.ESTADO_PUBLICADA);

            ofertaRepository.saveAll(List.of(o1, o2, o3));
        };
    }
}