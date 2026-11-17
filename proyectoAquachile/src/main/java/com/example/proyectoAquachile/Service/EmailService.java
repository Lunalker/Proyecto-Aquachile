package com.example.proyectoAquachile.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String remitente;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${spring.mail.username}") String remitente) {
        this.mailSender = mailSender;
        this.remitente = remitente;
    }

    public void enviarCodigoVerificacion(String destinatario, String nombreCompleto, String codigo) {
        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, "UTF-8");

            helper.setFrom(remitente, "AquaChile · Gestión Psicolaboral");
            helper.setTo(destinatario);
            helper.setSubject("Código de verificación: " + codigo + " - AquaChile");

            String html = """
                    <div style="font-family: Arial, sans-serif; max-width: 480px; margin: 0 auto; padding: 24px; border: 1px solid #e0e8ee; border-radius: 12px;">
                      <h2 style="color: #102c42; margin-top: 0;">Verifica tu correo en AquaChile</h2>
                      <p style="color: #486277;">Hola <strong>%s</strong>,</p>
                      <p style="color: #486277;">Usa el siguiente código de 6 dígitos para activar tu cuenta. Expira en 10 minutos:</p>
                      <div style="background: #f4f7f9; border: 1px solid #d8e4ea; border-radius: 8px; padding: 16px; text-align: center; margin: 20px 0;">
                        <span style="font-size: 32px; font-weight: bold; letter-spacing: 8px; color: #16758f;">%s</span>
                      </div>
                      <p style="color: #8294a4; font-size: 12px;">Si no solicitaste este registro, puedes ignorar este mensaje.</p>
                    </div>
                    """.formatted(nombreCompleto, codigo);

            helper.setText(html, true);
            mailSender.send(mensaje);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new RuntimeException("No se pudo enviar el correo vía Gmail SMTP: " + e.getMessage(), e);
        }
    }
}