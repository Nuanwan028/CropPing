package cropping.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cropping.dto.Event;
import cropping.dto.LineWebhookRequest;
import cropping.service.CropService;
import cropping.service.LineService;
import cropping.service.SchedulerService;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;


@RestController
@RequestMapping("/webhook")
public class WebhookController {

    private final CropService cropService;
    private final LineService lineService;
    private final SchedulerService scheduler;

    private static final ZoneId ZONE = ZoneId.of("Asia/Bangkok");

    public WebhookController(CropService cropService, LineService lineService, SchedulerService scheduler) {
        this.cropService = cropService;
        this.lineService = lineService;
        this.scheduler = scheduler;
    }

    @PostMapping
    public ResponseEntity<Void> handleWebhook(@RequestBody LineWebhookRequest request) {

        for (Event event : request.getEvents()) {

            if ("message".equals(event.getType()) &&
                    "text".equals(event.getMessage().getType())) {

                String userId = event.getSource().getUserId();
                String text = event.getMessage().getText();
                String replyToken = event.getReplyToken();

                if (text.equalsIgnoreCase("plant")) {
                    lineService.replyFlexMenu(replyToken);
                    return ResponseEntity.ok().build();
                }

                if (text.equalsIgnoreCase("list")) {
                    cropService.handleList(userId, replyToken);
                    return ResponseEntity.ok().build();
                }

                if (text.equalsIgnoreCase("time")) {
                    lineService.reply(replyToken, "⏰ ใส่เวลาแบบนี้:\nเช่น: 1:30 | 1.30 | 1 30");
                    return ResponseEntity.ok().build();
                }

                if (text.matches(".*\\d+.*")) {

                    try {
                        String cleaned = text
                                .toLowerCase()
                                .replace("time", "")
                                .replace(".", ":")
                                .trim();

                        String[] parts = cleaned.split("[:\\s]+");

                        int hours = Integer.parseInt(parts[0]);
                        int minutes = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;

                        ZonedDateTime result = ZonedDateTime.now(ZONE)
                                .plusHours(hours)
                                .plusMinutes(minutes);

                        DateTimeFormatter formatter = DateTimeFormatter
                                .ofPattern("dd MMM HH:mm")
                                .withZone(ZONE);

                        String formattedTime = formatter.format(result);

                        lineService.replyTimeWithSetButton(
                                replyToken,
                                hours,
                                minutes,
                                formattedTime // ส่งเป็น String ไปเลย
                        );

                        return ResponseEntity.ok().build();

                    } catch (Exception e) {
                        // ปล่อยให้ไปเข้า crop ต่อ
                    }
                }

                if (text.startsWith("settime")) {

                    String input = text.substring(8).trim();
                    String[] parts = input.split(":");

                    int hours = Integer.parseInt(parts[0]);
                    int minutes = Integer.parseInt(parts[1]);

                    cropService.createReminder(userId, hours, minutes, replyToken);

                    return ResponseEntity.ok().build();
                }

                if (text.startsWith("cancel:")) {
                    Long id = Long.parseLong(text.split(":")[1]);
                    cropService.cancelCrop(id, replyToken);
                    return ResponseEntity.ok().build();
                }

                if (text.equals("cancel_all")) {
                    cropService.cancelAll(userId, replyToken);
                    return ResponseEntity.ok().build();
                }

                cropService.handleUserMessage(userId, text, replyToken);
            }

        }

        return ResponseEntity.ok().build();
    }
}