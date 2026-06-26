package cropping.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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

                if (text.startsWith("cancel:")) {
                    Long id = Long.parseLong(text.split(":")[1]);
                    cropService.cancelCrop(id, replyToken);
                    return ResponseEntity.ok().build();
                }

                if (text.equals("cancel_all")) {
                    cropService.cancelAll(userId, replyToken);
                    return ResponseEntity.ok().build();
                }

                cropService.handleUserMessageLine(userId, text, replyToken);
            }

        }

        return ResponseEntity.ok().build();
    }

    @GetMapping("/ping")
    public String ping() {
        return "ok";
    }
}