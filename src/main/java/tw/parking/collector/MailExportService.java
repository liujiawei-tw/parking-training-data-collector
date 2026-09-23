package tw.parking.collector;

import jakarta.mail.internet.MimeMessage;
import java.nio.file.Path;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class MailExportService {
    private final JavaMailSender sender;
    private final String from;
    private final String to;

    public MailExportService(JavaMailSender sender,
                             @Value("${EXPORT_MAIL_FROM:${SMTP_USERNAME:}}") String from,
                             @Value("${EXPORT_MAIL_TO:}") String to) {
        this.sender = sender;
        this.from = from;
        this.to = to;
    }

    public void sendDailyExport(LocalDate date, Path zip) throws Exception {
        if (from == null || from.isBlank() || to == null || to.isBlank()) {
            throw new IllegalStateException("EXPORT_MAIL_FROM and EXPORT_MAIL_TO must be configured");
        }
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(from);
        helper.setTo(to);
        helper.setSubject("Parking training data export - " + date);
        helper.setText("""
                Parking training data export is attached.

                Date: %s
                File: %s
                """.formatted(date, zip.getFileName()));
        helper.addAttachment(zip.getFileName().toString(), new FileSystemResource(zip));
        sender.send(message);
    }
}
