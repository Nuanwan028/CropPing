package cropping.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import cropping.dto.DiscordInteractionRequest;
import cropping.service.DiscordService;
import cropping.service.CropService;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Discord Controller - รับ Interaction จาก Discord Bot
 * Interaction Type:
 * - 1: PING
 * - 2: APPLICATION_COMMAND (Slash Command)
 * - 3: MESSAGE_COMPONENT (Button click)
 */
@RestController
@RequestMapping(value = "/discord", produces = MediaType.APPLICATION_JSON_VALUE)
public class DiscordController {

    private final DiscordService discordService;
    private final CropService cropService;

    public DiscordController(DiscordService discordService, CropService cropService) {
        this.discordService = discordService;
        this.cropService = cropService;
    }

    /**
     * Endpoint สำหรับ Discord Interaction Webhook
     * Discord จะส่ง request มาที่นี่เมื่อ:
     * - User พิมพ์ slash command
     * - User กดปุ่ม
     * - Discord ping เพื่อ verify
     */
    @PostMapping("/interactions")
    public ResponseEntity<Map<String, Object>> handleInteraction(
            @RequestBody DiscordInteractionRequest request) {

        System.out.println("=== Discord Interaction ===");
        System.out.println("Type: " + request.getType());
        System.out.println("User ID: " + request.getUserId());

        // Type 1: PING - Discord verify endpoint
        if (request.getType() == 1) {
            System.out.println("PING received - returning PONG");
            return pong();
        }

        String userId = request.getUserId();
        String channelId = request.getChannelId();

        // Type 2: SLASH COMMAND
        if (request.getType() == 2 && request.getData() != null) {
            String commandName = request.getData().getName();
            System.out.println("Slash Command: " + commandName);

            return switch (commandName) {
                case "plant" -> handlePlantCommand(request, channelId);
                case "list" -> handleListCommand(userId, channelId);
                case "cancel" -> handleCancelCommand(request, channelId);
                case "cancel_all" -> handleCancelAllCommand(userId, channelId);
                default -> ack("❌ ไม่รู้จักคำสั่งนี้");
            };
        }

        // Type 3: BUTTON CLICK
        if (request.getType() == 3 && request.getData() != null) {
            String customId = request.getData().getCustomId();
            System.out.println("Button Click: " + customId);
            return handleButtonClick(customId, userId, channelId);
        }

        return ack("❌ เกิดข้อผิดพลาด");
    }

    /**
     * ตอบ PING กลับ Discord (Type 1)
     */
    private ResponseEntity<Map<String, Object>> pong() {
        Map<String, Object> response = new HashMap<>();
        response.put("type", 1); // PONG

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        return ResponseEntity.ok().headers(headers).body(response);
    }

    /**
     * ACK - ตอบกลับว่ารับแล้ว (สำหรับ command ที่ไม่ต้องการตอบทันที)
     */
    private ResponseEntity<Map<String, Object>> ack(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("type", 4); // MESSAGE_WITH_SOURCE

        Map<String, Object> data = new HashMap<>();
        data.put("content", message);
        // ลบ flags ออกก่อน - ทดสอบว่าเป็นปัญหาไหม
        // data.put("flags", 64); // EPHEMERAL - เห็นเฉพาะคนสั่ง

        response.put("data", data);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        System.out.println("ACK Response: " + response);

        return ResponseEntity.ok().headers(headers).body(response);
    }

    /**
     * ACK แบบมี embed
     */
    private ResponseEntity<Map<String, Object>> ackEmbed(String title, String description, int color) {
        Map<String, Object> response = new HashMap<>();
        response.put("type", 4); // MESSAGE_WITH_SOURCE

        Map<String, Object> data = new HashMap<>();
        data.put("flags", 64); // EPHEMERAL

        Map<String, Object> embed = new HashMap<>();
        embed.put("title", title);
        embed.put("description", description);
        embed.put("color", color);

        data.put("embeds", new Object[]{embed});
        response.put("data", data);

        return ResponseEntity.ok(response);
    }

    // ========== Command Handlers ==========

    /**
     /plant - เปิดเมนูเลือกพืช
     /plant <crop> - ปลูกพืชเลย
     */
    private ResponseEntity<Map<String, Object>> handlePlantCommand(
            DiscordInteractionRequest request, String channelId) {

        // ถ้ามี argument (crop name)
        String cropName = request.getData().getOptionValue("crop");

        if (cropName != null) {
            // ปลูกพืชเลย - ทำ async เพื่อไม่ให้ timeout
            CompletableFuture.runAsync(() -> {
                cropService.handleUserMessage(request.getUserId(), cropName, channelId);
            });
            return ack("✅ กำลังปลูก...");
        }

        // ไม่มี argument - ส่งเมนู (ทำ async เพื่อไม่ให้ timeout)
        CompletableFuture.runAsync(() -> {
            discordService.sendPlantMenu(channelId);
        });
        return ack("🌱 เลือกพืชที่จะปลูก:");
    }

    /**
     /list - ดูรายการพืชทั้งหมด
     */
    private ResponseEntity<Map<String, Object>> handleListCommand(String userId, String channelId) {
        // ทำ async เพื่อไม่ให้ timeout
        CompletableFuture.runAsync(() -> {
            cropService.handleList(userId, channelId, "DISCORD");
        });
        return ack("📋 กำลังดึงรายการพืช...");
    }

    /**
     /cancel <id> - ยกเลิกพืช
     */
    private ResponseEntity<Map<String, Object>> handleCancelCommand(
            DiscordInteractionRequest request, String channelId) {

        String idStr = request.getData().getOptionValue("id");
        if (idStr == null) {
            return ack("❌ กรุณาระบุ ID พืช");
        }

        try {
            Long id = Long.parseLong(idStr);
            // ทำ async เพื่อไม่ให้ timeout
            CompletableFuture.runAsync(() -> {
                cropService.cancelCrop(id, channelId, "DISCORD");
            });
            return ack("❌ ยกเลิกเรียบร้อย");
        } catch (NumberFormatException e) {
            return ack("❌ ID ไม่ถูกต้อง");
        }
    }

    /**
     /cancel_all - ยกเลิกทั้งหมด
     */
    private ResponseEntity<Map<String, Object>> handleCancelAllCommand(String userId, String channelId) {
        // ทำ async เพื่อไม่ให้ timeout
        CompletableFuture.runAsync(() -> {
            cropService.cancelAll(userId, channelId, "DISCORD");
        });
        return ack("🗑️ ลบทั้งหมดแล้ว");
    }

    // ========== Button Handlers ==========

    /**
     * จัดการเมื่อ user กดปุ่มเลือกพืช
     */
    private ResponseEntity<Map<String, Object>> handleButtonClick(
            String customId, String userId, String channelId) {

        // customId คือชื่อพืช (paddy, tomato, etc.)
        if (customId != null) {
            // ทำ async เพื่อไม่ให้ timeout
            CompletableFuture.runAsync(() -> {
                cropService.handleUserMessage(userId, customId, channelId);
            });
            return ack("✅ กำลังปลูก " + getCropDisplayName(customId) + "...");
        }

        return ack("❌ เกิดข้อผิดพลาด");
    }

    private String getCropDisplayName(String cropName) {
        return switch (cropName.toLowerCase()) {
            case "tomato" -> "🍅 มะเขือเทศ";
            case "paddy" -> "🌾 ข้าว";
            case "pineapple" -> "🍍 สับปะรด";
            case "tea" -> "🍃 ชา";
            case "potato" -> "🥔 มันฝรั่ง";
            case "carrot" -> "🥕 แครอท";
            case "wheat" -> "🌾 ข้าวสาลี";
            case "cacao" -> "🍫 โกโก้";
            case "strawberry" -> "🍓 สตรอว์เบอร์รี่";
            case "eggplant" -> "🍆 มะเขือ";
            case "lettuce" -> "🥬 ผักกาด";
            case "grape" -> "🍇 องุ่น";
            case "corn" -> "🌽 ข้าวโพด";
            case "avocado" -> "🥑 อะโวคาโด";
            default -> cropName;
        };
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/ping")
    public String ping() {
        return "Discord bot is running!";
    }

    /**
     * Register Slash Commands - รันครั้งเดียวตอน setup
     * GET http://localhost:8080/discord/register-commands
     */
    @GetMapping("/register-commands")
    public String registerCommands() {
        try {
            discordService.registerSlashCommands();
            return "✅ Slash Commands registered successfully!";
        } catch (Exception e) {
            return "❌ Error: " + e.getMessage();
        }
    }
}
