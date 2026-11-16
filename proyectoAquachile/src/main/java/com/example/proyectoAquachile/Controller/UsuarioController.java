package com.example.proyectoAquachile.Controller;

import com.example.proyectoAquachile.Repository.UsuarioRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioRepository usuarioRepository;

    public UsuarioController(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping("/evaluadores")
    public List<?> listarEvaluadores() {
        return usuarioRepository.findByRolAndActivoTrue("EVALUADOR").stream()
                .map(u -> Map.<String, Object>of(
                        "id", u.getId(),
                        "nombreCompleto", u.getNombreCompleto(),
                        "username", u.getUsername(),
                        "correo", u.getCorreo()
                ))
                .toList();
    }
}