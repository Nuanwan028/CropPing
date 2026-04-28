package cropping.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import cropping.entity.Crop;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LineService {

    @Value("${line.channel.token}")
    private String channelToken;

    private final RestTemplate restTemplate = new RestTemplate();

    private final String PUSH_URL = "https://api.line.me/v2/bot/message/push";
    private final String REPLY_URL = "https://api.line.me/v2/bot/message/reply";

    public void push(String userId, String text) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(channelToken);

        Map<String, Object> body = new HashMap<>();
        body.put("to", userId);

        Map<String, String> message = new HashMap<>();
        message.put("type", "text");
        message.put("text", text);

        body.put("messages", List.of(message));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        restTemplate.postForEntity(PUSH_URL, request, String.class);
    }

    public void reply(String replyToken, String text) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(channelToken);

        Map<String, Object> body = new HashMap<>();
        body.put("replyToken", replyToken);

        Map<String, String> message = new HashMap<>();
        message.put("type", "text");
        message.put("text", text);

        body.put("messages", List.of(message));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        restTemplate.postForEntity(REPLY_URL, request, String.class);
    }

    public void replyFlexMenu(String replyToken) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(channelToken);

        Map<String, Object> body = new HashMap<>();
        body.put("replyToken", replyToken);

        Map<String, Object> flexMessage = new HashMap<>();
        flexMessage.put("type", "flex");
        flexMessage.put("altText", "เลือกพืชที่จะปลูก");

        Map<String, Object> bubble = new HashMap<>();
        bubble.put("type", "bubble");

        Map<String, Object> bodyBox = new HashMap<>();
        bodyBox.put("type", "box");
        bodyBox.put("layout", "vertical");
        bodyBox.put("paddingAll", "20px"); // เพิ่มพื้นที่รอบด้าน
        bodyBox.put("spacing", "md");     // เว้นระยะห่างระหว่างลูกๆ ใน box

        List<Object> contents = new ArrayList<>();

        // Title Section
        contents.add(Map.of(
                "type", "text",
                "text", "ปลูกอะไร 🌱❔",
                "weight", "bold",
                "size", "xl",
                "margin", "none"
        ));

        // เส้นคั่นด้านบน
        contents.add(Map.of("type", "separator", "margin", "lg"));

        // crops
        contents.add(createCropBox("🍅 Tomato", "15 นาที", "tomato"));
        contents.add(Map.of("type", "separator", "margin", "md"));

        contents.add(createCropBox("🍍 Pineapple", "30 นาที", "pineapple"));
        contents.add(Map.of("type", "separator", "margin", "md"));

        contents.add(createCropBox("🥔 Potato", "60 นาที", "potato"));
        contents.add(Map.of("type", "separator", "margin", "md"));

        contents.add(createCropBox("🥕 Carrot", "2 ชม.", "carrot"));
        contents.add(Map.of("type", "separator", "margin", "md"));

        contents.add(createCropBox("🌾 Wheat", "4 ชม.", "wheat"));
        contents.add(Map.of("type", "separator", "margin", "md"));

        contents.add(createCropBox("🍓 Strawberry", "6 ชม.", "strawberry"));
        contents.add(Map.of("type", "separator", "margin", "md"));

        contents.add(createCropBox("🍆 Eggplant", "7 ชม.", "eggplant"));
        contents.add(Map.of("type", "separator", "margin", "md"));

        contents.add(createCropBox("🥬 Lettuce", "8 ชม.", "lettuce"));
        contents.add(Map.of("type", "separator", "margin", "md"));

        contents.add(createCropBox("🍇 Grape", "10 ชม.", "grape"));
        contents.add(Map.of("type", "separator", "margin", "md"));

        contents.add(createCropBox("🌽 Corn", "12 ชม.", "corn"));

        bodyBox.put("contents", contents);
        bubble.put("body", bodyBox);
        flexMessage.put("contents", bubble);

        body.put("messages", List.of(flexMessage));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        restTemplate.postForEntity(REPLY_URL, request, String.class);
    }

    private Map<String, Object> createCropBox(String name, String time, String value) {
        return Map.of(
                "type", "box",
                "layout", "horizontal",
                "alignItems", "center",
                "paddingAll", "12px",
                "spacing", "md",
                "action", Map.of(       // Action (กดได้ทั้งแถว)
                        "type", "message",
                        "label", "ปลูก " + name,
                        "text", value
                ),
                "contents", List.of(
                        // ชื่อพืช
                        Map.of(
                                "type", "text",
                                "text", name,
                                "flex", 1,
                                "weight", "bold",
                                "size", "md"
                        ),
                        // เวลา (ชิดขวา)
                        Map.of(
                                "type", "text",
                                "text", "⏱ " + time,
                                "flex", 0,
                                "size", "sm",
                                "color", "#888888",
                                "align", "end"
                        )

                )
        );
    }

    public void replyCropList(String replyToken, List<Crop> crops) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(channelToken);

        Map<String, Object> body = new HashMap<>();
        body.put("replyToken", replyToken);

        List<Object> contents = new ArrayList<>();

        contents.add(Map.of(
                "type", "text",
                "text", "📋 รายการที่ปลูก",
                "weight", "bold",
                "size", "xl"
        ));

        contents.add(Map.of("type", "separator", "margin", "lg"));

        for (Crop c : crops) {

            String displayText;
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM HH:mm");

            if ("reminder".equals(c.getType())) {
                displayText = "แจ้งเตือน\n ⏰" + c.getHarvestTime().format(formatter);
            } else {
                displayText = c.getCropName() +
                        "\n⏰" + c.getHarvestTime().format(formatter);
            }

            contents.add(Map.of(
                    "type", "box",
                    "layout", "horizontal",
                    "paddingAll", "10px",
                    "action", Map.of(
                            "type", "message",
                            "label", "ยกเลิก",
                            "text", "cancel:" + c.getId()
                    ),
                    "contents", List.of(
                            Map.of(
                                    "type", "text",
                                    "text", displayText,
                                    "flex", 1
                            ),
                            Map.of(
                                    "type", "text",
                                    "text", "❌",
                                    "flex", 0,
                                    "align", "end"
                            )
                    )
            ));

            contents.add(Map.of("type", "separator", "margin", "sm"));
        }

        // ปุ่มลบทั้งหมด
        contents.add(Map.of(
                "type", "button",
                "style", "primary",
                "color", "#ff5555",
                "action", Map.of(
                        "type", "message",
                        "label", "ลบทั้งหมด",
                        "text", "cancel_all"
                )
        ));

        Map<String, Object> bubble = Map.of(
                "type", "bubble",
                "body", Map.of(
                        "type", "box",
                        "layout", "vertical",
                        "contents", contents
                )
        );

        Map<String, Object> flex = Map.of(
                "type", "flex",
                "altText", "รายการพืช",
                "contents", bubble
        );

        body.put("messages", List.of(flex));

        restTemplate.postForEntity(REPLY_URL, new HttpEntity<>(body, headers), String.class);
    }

    public void replyTimeWithSetButton(String replyToken, int hours, int minutes, LocalDateTime result) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(channelToken);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM HH:mm");

        Map<String, Object> body = new HashMap<>();
        body.put("replyToken", replyToken);

        Map<String, Object> flexMessage = new HashMap<>();
        flexMessage.put("type", "flex");
        flexMessage.put("altText", "ผลลัพธ์เวลา");

        Map<String, Object> bubble = new HashMap<>();
        bubble.put("type", "bubble");

        // ===== BODY =====
        Map<String, Object> bodyBox = new HashMap<>();
        bodyBox.put("type", "box");
        bodyBox.put("layout", "vertical");
        bodyBox.put("spacing", "md");

        bodyBox.put("contents", List.of(
                Map.of(
                        "type", "text",
                        "text", "⏰ คำนวณเวลา",
                        "weight", "bold",
                        "size", "lg"
                ),
                Map.of(
                        "type", "text",
                        "text", "อีก " + hours + " ชม " + minutes + " นาที",
                        "size", "md"
                ),
                Map.of(
                        "type", "text",
                        "text", "👉 จะเป็นเวลา " + result.format(formatter),
                        "size", "md",
                        "weight", "bold"
                )
        ));

        // ===== FOOTER (ปุ่ม) =====
        Map<String, Object> footer = new HashMap<>();
        footer.put("type", "box");
        footer.put("layout", "vertical");

        footer.put("contents", List.of(
                Map.of(
                        "type", "button",
                        "style", "primary",
                        "action", Map.of(
                                "type", "message",
                                "label", "⏰ ตั้งแจ้งเตือน",
                                "text", "settime " + hours + ":" + minutes
                        )
                )
        ));

        bubble.put("body", bodyBox);
        bubble.put("footer", footer);

        flexMessage.put("contents", bubble);
        body.put("messages", List.of(flexMessage));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        restTemplate.postForEntity(REPLY_URL, request, String.class);
    }
}