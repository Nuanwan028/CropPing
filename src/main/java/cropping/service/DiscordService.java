package cropping.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import cropping.entity.Crop;

import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Discord Bot Service - สำหรับส่งข้อความและรับคำสั่งจาก user
 * รองรับ DM (Direct Message) และ Slash Commands
 */
@Service
public class DiscordService {

    @Value("${discord.bot.token}")
    private String botToken;

    @Value("${discord.application.id}")
    private String applicationId;

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String API_BASE = "https://discord.com/api/v10";

    // ========== ส่งข้อควาย ==========

    /**
     * ส่งข้อความตอบกลับ user ใน channel ที่กำหนด
     */
    public void sendMessage(String channelId, String text) {
        String url = API_BASE + "/channels/" + channelId + "/messages";

        Map<String, Object> body = new HashMap<>();
        body.put("content", text);

        HttpHeaders headers = getHeaders();
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        restTemplate.postForEntity(url, request, String.class);
    }

    /**
     * ส่ง embed แบบสวยงาม
     */
    public void sendEmbed(String channelId, String title, String description, int color, List<Map<String, String>> fields) {

        String url = API_BASE + "/channels/" + channelId + "/messages";

        Map<String, Object> body = new HashMap<>();

        Map<String, Object> embed = new HashMap<>();
        embed.put("title", title);
        embed.put("description", description);
        embed.put("color", color);
        embed.put("timestamp", java.time.Instant.now().toString());

        if (fields != null && !fields.isEmpty()) {
            List<Map<String, Object>> embedFields = new ArrayList<>();
            for (Map<String, String> field : fields) {
                embedFields.add(Map.of(
                        "name", field.get("name"),
                        "value", field.get("value"),
                        "inline", field.getOrDefault("inline", "false")
                ));
            }
            embed.put("fields", embedFields);
        }

        body.put("embeds", List.of(embed));

        HttpHeaders headers = getHeaders();
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        restTemplate.postForEntity(url, request, String.class);
    }

    /**
     * ส่งเมนูเลือกพืชแบบ Component (Buttons)
     */
    public void sendPlantMenu(String channelId) {

        String url = API_BASE + "/channels/" + channelId + "/messages";

        Map<String, Object> body = new HashMap<>();
        body.put("content", "🌱 เลือกพืชที่จะปลูก:");

        Map<String, Object> embed = new HashMap<>();
        embed.put("title", "ปลูกอะไรดี?");
        embed.put("description", "กดเลือกพืชที่คุณอยากปลูก");
        embed.put("color", 0x5865F2);
        body.put("embeds", List.of(embed));

        // Action Rows with Buttons
        List<Map<String, Object>> actionRows = new ArrayList<>();

        // Row 1: ปุ่มพืชแต่ละอัน
        actionRows.add(Map.of(
                "type", 1,
                "components", List.of(
                        createButton("paddy", "🌾 ข้าว", 0x57F287),
                        createButton("tomato", "🍅 มะเขือเทศ", 0xED4245),
                        createButton("corn", "🌽 ข้าวโพด", 0xFEE75C),
                        createButton("pineapple", "🍍 สับปะรด", 0xFEE75C),
                        createButton("tea", "🍃 ชา", 0x57F287)
                )
        ));

        // Row 2: พืชที่ใช้เวลานานขึ้น
        actionRows.add(Map.of(
                "type", 1,
                "components", List.of(
                        createButton("wheat", "🌾 ข้าวสาลี", 0xFEE75C),
                        createButton("potato", "🥔 มันฝรั่ง", 0xCFC3B3),
                        createButton("carrot", "🥕 แครอท", 0xED4245),
                        createButton("lettuce", "🥬 ผักกาด", 0x57F287)
                )
        ));

        // Row 3: พืชที่ใช้เวลายาวนาน
        actionRows.add(Map.of(
                "type", 1,
                "components", List.of(
                        createButton("grape", "🍇 องุ่น", 0x5865F2),
                        createButton("strawberry", "🍓 สตรอว์เบอร์รี่", 0xED4245),
                        createButton("avocado", "🥑 อะโวคาโด", 0x57F287),
                        createButton("cacao", "🍫 โกโก้", 0x3C3A41)
                )
        ));

        body.put("components", actionRows);

        HttpHeaders headers = getHeaders();
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        restTemplate.postForEntity(url, request, String.class);
    }

    private Map<String, Object> createButton(String customId, String label, int color) {
        return Map.of(
                "type", 2,
                "style", 1, // Primary button (blue)
                "custom_id", customId,
                "label", label,
                "emoji", Map.of("name", getEmojiForCrop(customId))
        );
    }

    private String getEmojiForCrop(String cropName) {
        return switch (cropName.toLowerCase()) {
            case "tomato" -> "🍅";
            case "paddy" -> "🌾";
            case "pineapple" -> "🍍";
            case "tea" -> "🍃";
            case "potato" -> "🥔";
            case "carrot" -> "🥕";
            case "wheat" -> "🌾";
            case "cacao" -> "🍫";
            case "strawberry" -> "🍓";
            case "eggplant" -> "🍆";
            case "lettuce" -> "🥬";
            case "grape" -> "🍇";
            case "corn" -> "🌽";
            case "avocado" -> "🥑";
            default -> "🌱";
        };
    }

    /**
     * ส่งรายการพืชทั้งหมดที่ปลูกอยู่
     */
    public void sendCropList(String channelId, List<Crop> crops) {

        List<Map<String, String>> fields = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM HH:mm");

        for (Crop crop : crops) {
            // กรองพืชที่หมดอายุแล้ว
            if (crop.getHarvestTime().isBefore(java.time.LocalDateTime.now())) {
                continue;
            }

            String displayName = getCropDisplayName(crop.getCropName());
            fields.add(Map.of(
                    "name", displayName,
                    "value", "⏱ " + crop.getHarvestTime().format(formatter) + " | ID: `" + crop.getId() + "`",
                    "inline", "false"
            ));
        }

        if (fields.isEmpty()) {
            sendEmbed(channelId, "📋 รายการพืช", "ไม่มีพืชที่ปลูกอยู่", 0xED4245, null);
        } else {
            sendEmbed(channelId, "📋 รายการพืชที่ปลูก", null, 0x5865F2, fields);
        }
    }

    /**
     * ส่งข้อความแจ้งเตือนเมื่อถึงเวลาเก็บเกี่ยว
     */
    public void sendHarvestNotification(String channelId, Crop crop) {

        String displayName = getCropDisplayName(crop.getCropName());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM HH:mm");

        String description = String.format(
                "พืช **%s** โตเต็มที่แล้ว!\nเวลาเก็บเกี่ยว: **%s**",
                displayName, crop.getHarvestTime().format(formatter)
        );

        sendEmbed(channelId, "🌱 ถึงเวลาเก็บเกี่ยว!", description, 0x57F287, null);
    }

    /**
     * ส่งข้อความเมื่อปลูกพืชสำเร็จ
     */
    public void sendPlantSuccess(String channelId, String cropName, String harvestTime) {

        String displayName = getCropDisplayName(cropName);

        sendEmbed(channelId,
                "✅ ปลูกพืชสำเร็จ!",
                String.format("เริ่มปลูก **%s** แล้ว\nจะเก็บเกี่ยวเมื่อถึงเวลา: **%s**", displayName, harvestTime),
                0x57F287, null);
    }

    /**
     * ส่งข้อความเมื่อยกเลิกพืช
     */
    public void sendCropCancelled(String channelId, String cropName, String cropId) {

        String displayName = getCropDisplayName(cropName);

        sendEmbed(channelId,
                "🗑️ ยกเลิกการปลูก",
                String.format("ยกเลิก **%s** (ID: `%s`) เรียบร้อยแล้ว", displayName, cropId),
                0xED4245, null);
    }

    // ========== Slash Commands ==========

    /**
     * ลงทะเบียน Slash Commands กับ Discord
     * ต้องเรียกครั้งเดียวตอน setup หรือเมื่ออัปเดต commands
     */
    public void registerSlashCommands() {

        String url = API_BASE + "/applications/" + applicationId + "/commands";

        List<Map<String, Object>> commands = new ArrayList<>();

        // /plant <crop> command (single command with optional argument)
        Map<String, Object> plantCommand = new HashMap<>();
        plantCommand.put("name", "plant");
        plantCommand.put("description", "ปลูกพืช - ไม่ใส่ค่าจะเปิดเมนูเลือก, ใส่ชื่อพืชจะปลูกเลย");
        plantCommand.put("type", 1);
        plantCommand.put("options", List.of(Map.of(
                "type", 3, // STRING
                "name", "crop",
                "description", "ชื่อพืช เช่น paddy, tomato, corn (เว้นว่างเพื่อเปิดเมนู)",
                "required", false, // Changed to false - allow both with and without argument
                "choices", getCropChoices()
        )));
        commands.add(plantCommand);

        // /list command
        commands.add(Map.of(
                "name", "list",
                "description", "ดูรายการพืชที่ปลูกอยู่ทั้งหมด",
                "type", 1
        ));

        // /cancel <id> command
        commands.add(Map.of(
                "name", "cancel",
                "description", "ยกเลิกการปลูกพืช",
                "type", 1,
                "options", List.of(Map.of(
                        "type", 4, // INTEGER
                        "name", "id",
                        "description", "รหัสพืชที่จะยกเลิก",
                        "required", true
                ))
        ));

        // /cancel_all command
        commands.add(Map.of(
                "name", "cancel_all",
                "description", "ยกเลิกการปลูกพืชทั้งหมด",
                "type", 1
        ));

        HttpHeaders headers = getHeaders();
        HttpEntity<List<Map<String, Object>>> request = new HttpEntity<>(commands, headers);

        restTemplate.exchange(url, HttpMethod.PUT, request, String.class);
    }

    private List<Map<String, String>> getCropChoices() {
        return List.of(
                Map.of("name", "🌾 ข้าว", "value", "paddy"),
                Map.of("name", "🍅 มะเขือเทศ", "value", "tomato"),
                Map.of("name", "🌽 ข้าวโพด", "value", "corn"),
                Map.of("name", "🍍 สับปะรด", "value", "pineapple"),
                Map.of("name", "🍃 ชา", "value", "tea"),
                Map.of("name", "🌾 ข้าวสาลี", "value", "wheat"),
                Map.of("name", "🥔 มันฝรั่ง", "value", "potato"),
                Map.of("name", "🥕 แครอท", "value", "carrot"),
                Map.of("name", "🥬 ผักกาด", "value", "lettuce"),
                Map.of("name", "🍇 องุ่น", "value", "grape"),
                Map.of("name", "🍓 สตรอว์เบอร์รี่", "value", "strawberry"),
                Map.of("name", "🥑 อะโวคาโด", "value", "avocado"),
                Map.of("name", "🍫 โกโก้", "value", "cacao"),
                Map.of("name", "🍆 มะเขือ", "value", "eggplant")
        );
    }

    // ========== Helper Methods ==========

    private HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bot " + botToken); // Discord requires "Bot" prefix, not "Bearer"
        headers.set("User-Agent", "DiscordBot (https://github.com, 1.0)");
        return headers;
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
}
