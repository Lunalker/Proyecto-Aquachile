package com.example.proyectoAquachile.Config;

import com.example.proyectoAquachile.Model.Usuario;
import com.example.proyectoAquachile.Repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class InicializadorUsuarios implements CommandLineRunner {

    private final UsuarioRepository repo;
    private final PasswordEncoder encoder;
    private final String claveAnalista;
    private final String claveEvaluador;

    public InicializadorUsuarios(
        UsuarioRepository repo,
        PasswordEncoder encoder,
            @Value("${aquachile.analista.password:admin123}") String claveAnalista,
            @Value("${aquachile.evaluador.password:user123}") String claveEvaluador) {
        this.repo = repo;
        this.encoder = encoder;
        this.claveAnalista = claveAnalista;
        this.claveEvaluador = claveEvaluador;
    }

    @Override
    public void run(String... args) {
        if (!repo.existsByUsername("analista")) {
            Usuario a = new Usuario();
            a.setNombreCompleto("Analista Principal");
            a.setCorreo("analista@aquachile.cl");
            a.setUsername("analista");
            a.setPassword(encoder.encode(claveAnalista));
            a.setRol("ANALISTA");
            a.setActivo(true);
            repo.save(a);
        }
        if (!repo.existsByUsername("evaluador")) {
            Usuario e = new Usuario();
            e.setNombreCompleto("Evaluador Psicolaboral");
            e.setCorreo("evaluador@aquachile.cl");
            e.setUsername("evaluador");
            e.setPassword(encoder.encode(claveEvaluador));
            e.setRol("EVALUADOR");
            e.setActivo(true);
            repo.save(e);
        }
    }
}