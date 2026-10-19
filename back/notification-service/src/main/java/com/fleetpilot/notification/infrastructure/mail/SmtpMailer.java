package com.fleetpilot.notification.infrastructure.mail;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import com.fleetpilot.notification.application.Ports.Attachment;
import com.fleetpilot.notification.application.Ports.Mailer;
import com.fleetpilot.notification.application.Ports.OutgoingMail;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** Sends the e-mails over SMTP (Mailpit in development, the mail relay of the company in production). */
@Component
public class SmtpMailer implements Mailer {

    private final JavaMailSender sender;
    private final String fromAddress;
    private final String fromName;

    public SmtpMailer(JavaMailSender sender, @Value("${fleetpilot.notifications.from-address}") String fromAddress,
            @Value("${fleetpilot.notifications.from-name}") String fromName) {
        this.sender = sender;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
    }

    @Override
    public String send(OutgoingMail mail) {
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, !mail.attachments().isEmpty(),
                    StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress, fromName);
            if (mail.toName() != null) {
                helper.setTo(new InternetAddress(mail.to(), mail.toName(),
                        StandardCharsets.UTF_8.name()));
            } else {
                helper.setTo(mail.to());
            }
            helper.setSubject(mail.subject());
            helper.setText(mail.html(), true);
            for (Attachment attachment : mail.attachments()) {
                helper.addAttachment(attachment.fileName(), new ByteArrayResource(attachment.content()),
                        attachment.contentType());
            }
            sender.send(message);
            return message.getMessageID();
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new MailSendException("The e-mail could not be built", e);
        }
    }
}
