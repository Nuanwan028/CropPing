package cropping.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import cropping.entity.Crop;
import cropping.repository.CropRepository;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Discord Gateway Service - เชื่อมต่อ Discord ผ่าน WebSocket
 * Slash Commands จะส่งมาทาง Gateway แทน HTTP Endpoint
 */
@Service
public class DiscordGatewayService {

    private static final Logger logger = LoggerFactory.getLogger(DiscordGatewayService.class);
    private static final String GATEWAY_URL = "wss://gateway.discord.gg";
    private static final ZoneId ZONE = ZoneId.of("Asia/Bangkok");

    @Value("${discord.bot.token}")
    private String botToken;

    @Value("${discord.application.id}")
    private String applicationId;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final CropService cropService;
    private final DiscordService discordService;
    private final CropRepository cropRepository;
    private final SchedulerService schedulerService;

    private WebSocket webSocket;
    private volatile boolean isConnected = false;
    private volatile int heartbeatInterval = 41250; // default ~41 seconds
    private volatile long lastHeartbeatAck = 0;
    private volatile String sessionId = null;
    private volatile int sequenceNumber = 0;

    public DiscordGatewayService(CropService cropService, DiscordService discordService,
                                  CropRepository cropRepository, SchedulerService schedulerService) {
        this.cropService = cropService;
        this.discordService = discordService;
        this.cropRepository = cropRepository;
        this.schedulerService = schedulerService;
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for WebSocket
                .build();
    }

    /**
     * เริ่มเชื่อมต่อ Discord Gateway เมื่อ start application
     */
    @PostConstruct
    @Async
    public void connect() {
        try {
            logger.info("Discord Bot Token: {}...", botToken.substring(0, Math.min(20, botToken.length())));
            logger.info("Discord Application ID: {}", applicationId);

            // 1. Get Gateway URL (with bot params)
            String gatewayUrl = getGatewayUrl();
            logger.info("Connecting to Discord Gateway: {}", gatewayUrl);

            // 2. Create WebSocket connection
            Request request = new Request.Builder()
                    .url(gatewayUrl)
                    .build();

            webSocket = httpClient.newWebSocket(request, new DiscordWebSocketListener());

        } catch (Exception e) {
            logger.error("Failed to connect to Discord Gateway", e);
            // Retry after 5 seconds
            scheduleReconnect(5000);
        }
    }

    /**
     * ดึง Gateway URL จาก Discord API
     */
    private String getGatewayUrl() throws Exception {
        Request request = new Request.Builder()
                .url("https://discord.com/api/v10/gateway/bot")
                .addHeader("Authorization", "Bot " + botToken)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("Failed to get gateway URL: " + response.code());
            }

            JsonNode body = objectMapper.readTree(response.body().string());
            String url = body.get("url").asText();
            // Add query parameters for bot connection
            return url + "?v=10&encoding=json";
        }
    }

    /**
     * WebSocket Listener สำหรับรับ event จาก Discord
     */
    private class DiscordWebSocketListener extends WebSocketListener {

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            logger.info("Discord WebSocket connected!");
            isConnected = true;
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            try {
                handleGatewayMessage(text);
            } catch (Exception e) {
                logger.error("Error handling gateway message", e);
            }
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            logger.warn("WebSocket closing: {} - {}", code, reason);
            isConnected = false;
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            logger.error("WebSocket failure", t);
            isConnected = false;
            // Schedule reconnect
            scheduleReconnect(5000);
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            logger.warn("WebSocket closed: {} - {}", code, reason);
            isConnected = false;
            // Schedule reconnect with exponential backoff
            scheduleReconnect(5000);
        }
    }

    /**
     * จัดการข้อความจาก Discord Gateway
     */
    private void handleGatewayMessage(String message) throws Exception {
        JsonNode json = objectMapper.readTree(message);
        int op = json.get("op").asInt();

        switch (op) {
            case 0: // Dispatch - Event received
                handleEvent(json.get("d"), json.get("t"));
                break;

            case 1: // Heartbeat - Discord requesting heartbeat
                sendHeartbeat();
                break;

            case 10: // Hello - Discord sends heartbeat interval
                handleHello(json.get("d"));
                break;

            case 11: // Heartbeat ACK
                lastHeartbeatAck = System.currentTimeMillis();
                logger.debug("Heartbeat ACK received");
                break;

            case 9: // Invalid Session
                logger.warn("Invalid session! Reconnecting...");
                webSocket.close(1000, "Invalid session");
                scheduleReconnect(1000);
                break;

            default:
                logger.debug("Unknown opcode: {}", op);
        }
    }

    /**
     * จัดการ Event จาก Discord
     */
    private void handleEvent(JsonNode data, JsonNode eventType) throws Exception {
        if (eventType == null || data == null) {
            return;
        }

        String eventName = eventType.asText();
        logger.info("Received event: {}", eventName);

        // Update sequence number
        if (data.has("s")) {
            sequenceNumber = data.get("s").asInt();
        }

        switch (eventName) {
            case "READY":
                sessionId = data.get("session_id").asText();
                logger.info("Session ID: {}", sessionId);
                // Ready to receive events!
                break;

            case "APPLICATION_COMMAND":
            case "INTERACTION_CREATE":
            case "MESSAGE_COMPONENT":
                // All interactions are handled here
                handleInteraction(data);
                break;

            default:
                logger.debug("Unhandled event: {}", eventName);
        }
    }

    /**
     * จัดการ Slash Command / Interaction
     */
    private void handleInteraction(JsonNode data) throws Exception {
        int type = data.get("type").asInt();
        String interactionId = data.get("id").asText();
        String token = data.get("token").asText();
        String channelId = data.get("channel_id").asText();

        // Extract user ID (guild or DM)
        String userId = null;
        if (data.has("member") && data.get("member").has("user")) {
            userId = data.get("member").get("user").get("id").asText();
        } else if (data.has("user")) {
            userId = data.get("user").get("id").asText();
        } else if (data.has("guild_id")) {
            userId = data.get("guild_id").asText();
        }

        logger.info("Interaction received - Type: {}, User: {}, Channel: {}", type, userId, channelId);

        // Type 1: PING
        if (type == 1) {
            sendInteractionResponse(interactionId, token, createPongResponse());
            return;
        }

        // Type 2: APPLICATION_COMMAND
        if (type == 2 && data.has("data")) {
            JsonNode dataNode = data.get("data");
            String commandName = dataNode.get("name").asText();

            logger.info("Processing command: {} with deferred response", commandName);

            // CRITICAL: Send deferred response FIRST (within 3 seconds)
            sendInteractionResponse(interactionId, token, createDeferredResponse());

            // Then process asynchronously (can take longer)
            processCommandAsync(commandName, userId, channelId, dataNode, token);
            return;
        }

        // Type 3: MESSAGE_COMPONENT (Button)
        if (type == 3) {
            JsonNode dataNode = data.get("data");
            String customId = dataNode.get("custom_id").asText();

            logger.info("Processing button click: {} with deferred response", customId);

            // CRITICAL: Send deferred response FIRST (within 3 seconds)
            sendInteractionResponse(interactionId, token, createDeferredResponse());

            // Then process asynchronously
            processButtonClickAsync(customId, userId, channelId, token);
            return;
        }
    }

    /**
     * Process command asynchronously - edit original response
     */
    @Async
    protected void processCommandAsync(String commandName, String userId, String channelId, JsonNode data, String token) {
        try {
            logger.info("Async processing command: {} started", commandName);
            switch (commandName) {
                case "plant":
                    handlePlantCommandAsync(userId, channelId, data, token);
                    break;
                case "list":
                    handleListCommandAsync(userId, channelId, token);
                    break;
                case "cancel":
                    handleCancelCommandAsync(userId, channelId, data, token);
                    break;
                case "cancel_all":
                    handleCancelAllCommandAsync(userId, channelId, token);
                    break;
                default:
                    editOriginalResponse(token, "❌ ไม่รู้จักคำสั่งนี้");
            }
            logger.info("Async processing command: {} completed", commandName);
        } catch (Exception e) {
            logger.error("Error processing command: {}", commandName, e);
            editOriginalResponse(token, "❌ เกิดข้อผิดพลาด");
        }
    }

    /**
     * Process button click asynchronously - edit original response
     */
    @Async
    protected void processButtonClickAsync(String customId, String userId, String channelId, String token) {
        try {
            // Validate crop
            if (!isValidCrop(customId)) {
                editOriginalResponse(token, "❌ ไม่รู้จักพืชนี้");
                return;
            }

            // Get crop info for response
            String cropDisplay = getCropDisplayName(customId);
            int growTime = getGrowTime(customId);
            ZonedDateTime now = ZonedDateTime.now(ZONE);
            ZonedDateTime harvestTime = now.plusMinutes(growTime);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM HH:mm");
            String harvestTimeStr = harvestTime.format(formatter);

            // Save to database and schedule notifications
            saveCropAndSchedule(userId, channelId, customId);

            // Edit the deferred response with success message
            // Emoji is outside bold markdown to avoid duplication
            String message = String.format(
                "✅ ปลูกพืชสำเร็จ!\nเริ่มปลูก %s**%s** แล้ว\nจะเก็บเกี่ยวเมื่อถึงเวลา: **%s**",
                cropDisplay.split(" ", 2)[0] + " ",  // emoji
                cropDisplay.split(" ", 2)[1],         // name without emoji
                harvestTimeStr
            );
            editOriginalResponse(token, message);
            logger.info("Button click processed: {}", customId);
        } catch (Exception e) {
            logger.error("Error processing button click", e);
            editOriginalResponse(token, "❌ เกิดข้อผิดพลาด");
        }
    }

    private void handlePlantCommandAsync(String userId, String channelId, JsonNode data, String token) {
        if (data.has("options") && data.get("options").size() > 0) {
            // Direct plant with crop name
            String cropName = data.get("options").get(0).get("value").asText();

            if (!isValidCrop(cropName)) {
                editOriginalResponse(token, "❌ ไม่รู้จักพืชนี้");
                return;
            }

            String cropDisplay = getCropDisplayName(cropName);
            int growTime = getGrowTime(cropName);
            ZonedDateTime now = ZonedDateTime.now(ZONE);
            ZonedDateTime harvestTime = now.plusMinutes(growTime);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM HH:mm");
            String harvestTimeStr = harvestTime.format(formatter);

            // Save to database and schedule notifications
            saveCropAndSchedule(userId, channelId, cropName);

            // Edit response with success message
            // Emoji is outside bold markdown to avoid duplication
            String message = String.format(
                "✅ ปลูกพืชสำเร็จ!\nเริ่มปลูก %s**%s** แล้ว\nจะเก็บเกี่ยวเมื่อถึงเวลา: **%s**",
                cropDisplay.split(" ", 2)[0] + " ",  // emoji
                cropDisplay.split(" ", 2)[1],         // name without emoji
                harvestTimeStr
            );
            editOriginalResponse(token, message);
        } else {
            // No argument - show plant menu via follow-up (can't edit with buttons)
            sendPlantMenuFollowup(token);
        }
    }

    private void handleListCommandAsync(String userId, String channelId, String token) {
        var crops = cropRepository.findByUserId(userId);
        ZonedDateTime now = ZonedDateTime.now(ZONE);

        // Filter active crops
        var activeCrops = crops.stream()
                .filter(c -> c.getHarvestTime().isAfter(now.toLocalDateTime()))
                .toList();

        // Delete expired crops
        var expiredCrops = crops.stream()
                .filter(c -> !c.getHarvestTime().isAfter(now.toLocalDateTime()))
                .toList();
        if (!expiredCrops.isEmpty()) {
            cropRepository.deleteAll(expiredCrops);
        }

        if (activeCrops.isEmpty()) {
            editOriginalResponse(token, "📭 ไม่มีพืชที่ปลูกอยู่");
        } else {
            StringBuilder sb = new StringBuilder("📋 รายการพืชที่ปลูก\n\n");
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM HH:mm");
            for (var crop : activeCrops) {
                String displayName = getCropDisplayName(crop.getCropName());
                sb.append(String.format("%s\n⏱ %s | ID: `%d`\n\n",
                    displayName, crop.getHarvestTime().format(formatter), crop.getId()));
            }
            editOriginalResponse(token, sb.toString());
        }
    }

    private void handleCancelCommandAsync(String userId, String channelId, JsonNode data, String token) {
        if (data.has("options") && data.get("options").size() > 0) {
            Long id = data.get("options").get(0).get("value").asLong();
            cropRepository.deleteById(id);
            editOriginalResponse(token, "❌ ยกเลิกเรียบร้อย");
        } else {
            editOriginalResponse(token, "❌ กรุณาระบุ ID พืช");
        }
    }

    private void handleCancelAllCommandAsync(String userId, String channelId, String token) {
        var crops = cropRepository.findByUserId(userId);
        cropRepository.deleteAll(crops);
        editOriginalResponse(token, "🗑 ลบทั้งหมมดแล้ว");
    }

    /**
     * จัดการ Hello (เริ่ม connection)
     */
    private void handleHello(JsonNode data) {
        heartbeatInterval = data.get("heartbeat_interval").asInt();
        logger.info("Heartbeat interval: {}ms", heartbeatInterval);

        // 1. Identify with bot token
        sendIdentify();

        // 2. Start heartbeat
        startHeartbeat();
    }

    /**
     * Identify กับ Discord Gateway
     */
    private void sendIdentify() {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("op", 2); // Identify

            ObjectNode data = objectMapper.createObjectNode();
            data.put("token", botToken);
            data.put("intents", 33281); // Intents: GUILDS (1) + GUILD_MESSAGES (512) + MESSAGE_CONTENT (32768)

            ObjectNode connectionProperties = objectMapper.createObjectNode();
            connectionProperties.put("os", "windows");
            connectionProperties.put("browser", "cropping");
            connectionProperties.put("device", "cropping");

            data.set("properties", connectionProperties);

            payload.set("d", data);

            String json = objectMapper.writeValueAsString(payload);
            logger.info("Sending Identify");
            webSocket.send(json);
        } catch (Exception e) {
            logger.error("Error sending Identify", e);
        }
    }

    /**
     * เริ่ม Heartbeat loop
     */
    private void startHeartbeat() {
        Thread heartbeatThread = new Thread(() -> {
            while (isConnected) {
                try {
                    long now = System.currentTimeMillis();
                    long timeSinceLastAck = now - lastHeartbeatAck;

                    // If no ACK for 2 heartbeats, close and reconnect
                    if (lastHeartbeatAck > 0 && timeSinceLastAck > heartbeatInterval * 2) {
                        logger.warn("No heartbeat ACK received, reconnecting...");
                        if (webSocket != null) {
                            webSocket.close(1000, "No heartbeat ACK");
                        }
                        break;
                    }

                    sendHeartbeat();
                    Thread.sleep(heartbeatInterval);
                } catch (InterruptedException e) {
                    logger.info("Heartbeat thread interrupted");
                    break;
                } catch (Exception e) {
                    logger.error("Error in heartbeat loop", e);
                    break;
                }
            }
        });
        heartbeatThread.setName("Discord-Heartbeat");
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }

    /**
     * ส่ง Heartbeat
     */
    private void sendHeartbeat() {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("op", 1); // Heartbeat
            payload.put("d", sequenceNumber > 0 ? sequenceNumber : null);

            String json = objectMapper.writeValueAsString(payload);
            webSocket.send(json);
            logger.debug("Heartbeat sent");
        } catch (Exception e) {
            logger.error("Error sending heartbeat", e);
        }
    }

    /**
     * ส่ง Interaction Response
     */
    private void sendInteractionResponse(String interactionId, String interactionToken, ObjectNode response) {
        long startTime = System.currentTimeMillis();
        try {
            // Correct URL format: /interactions/{interactionId}/{interactionToken}/callback
            String url = "https://discord.com/api/v10/interactions/" + interactionId + "/" + interactionToken + "/callback";
            logger.info("Sending interaction response to: {}", url);
            logger.debug("Response body: {}", response.toString());

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bot " + botToken)
                    .addHeader("Content-Type", "application/json")
                    .post(okhttp3.RequestBody.create(response.toString(),
                            okhttp3.MediaType.parse("application/json")))
                    .build();

            try (Response httpResponse = httpClient.newCall(request).execute()) {
                long duration = System.currentTimeMillis() - startTime;
                if (!httpResponse.isSuccessful()) {
                    String errorBody = httpResponse.body() != null ? httpResponse.body().string() : "no body";
                    logger.error("Interaction response failed in {}ms - {} - Error: {}", duration, httpResponse.code(), errorBody);
                } else {
                    logger.info("Interaction response successful in {}ms", duration);
                }
            }
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("Error sending interaction response after {}ms", duration, e);
        }
    }

    /**
     * ส่ง Follow-up Message
     */
    private void sendFollowupMessage(String interactionToken, String message) {
        try {
            String url = "https://discord.com/api/v10/webhooks/" + applicationId + "/" + interactionToken;

            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("content", message);

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bot " + botToken)
                    .addHeader("Content-Type", "application/json")
                    .post(okhttp3.RequestBody.create(payload.toString(),
                            okhttp3.MediaType.parse("application/json")))
                    .build();

            try (Response httpResponse = httpClient.newCall(request).execute()) {
                if (!httpResponse.isSuccessful()) {
                    logger.error("Follow-up message failed: {}", httpResponse.code());
                }
            }
        } catch (Exception e) {
            logger.error("Error sending follow-up message", e);
        }
    }

    /**
     * แก้ไขข้อความเดิม (หลังจากใช้ Deferred Response)
     * URL: /webhooks/{applicationId}/{interactionToken}/messages/@original
     */
    private void editOriginalResponse(String interactionToken, String message) {
        try {
            String url = "https://discord.com/api/v10/webhooks/" + applicationId + "/" + interactionToken + "/messages/@original";

            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("content", message);

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bot " + botToken)
                    .addHeader("Content-Type", "application/json")
                    .patch(okhttp3.RequestBody.create(payload.toString(),
                            okhttp3.MediaType.parse("application/json")))
                    .build();

            try (Response httpResponse = httpClient.newCall(request).execute()) {
                if (!httpResponse.isSuccessful()) {
                    String errorBody = httpResponse.body() != null ? httpResponse.body().string() : "no body";
                    logger.error("Edit original response failed: {} - Error: {}", httpResponse.code(), errorBody);
                } else {
                    logger.debug("Edit original response successful");
                }
            }
        } catch (Exception e) {
            logger.error("Error editing original response", e);
        }
    }

    /**
     * สร้าง PONG Response (Type 1)
     */
    private ObjectNode createPongResponse() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("type", 1); // PONG
        return response;
    }

    /**
     * สร้าง Deferred Response (Type 5)
     */
    private ObjectNode createDeferredResponse() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("type", 5); // DEFERRED_CHANNEL_MESSAGE_WITH_SOURCE
        return response;
    }

    /**
     * สร้าง Message Response (Type 4) - Immediate response with message
     */
    private ObjectNode createMessageResponse(String message) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("type", 4); // MESSAGE_WITH_SOURCE

        // Add data with content
        ObjectNode data = response.putObject("data");
        data.put("content", message);

        return response;
    }

    /**
     * Send plant menu as follow-up message
     */
    private void sendPlantMenuFollowup(String interactionToken) {
        try {
            String url = "https://discord.com/api/v10/webhooks/" + applicationId + "/" + interactionToken;

            ObjectNode body = objectMapper.createObjectNode();
            body.put("content", "🌱 เลือกพืชที่จะปลูก:");

            // Add embed
            ArrayNode embedsArray = body.putArray("embeds");
            ObjectNode embed = embedsArray.addObject();
            embed.put("title", "ปลูกอะไรดี?");
            embed.put("description", "กดเลือกพืชที่คุณอยากปลูก");
            embed.put("color", 0x5865F2);

            // Add action rows with buttons
            body.set("components", createPlantMenuComponents());

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bot " + botToken)
                    .addHeader("Content-Type", "application/json")
                    .post(okhttp3.RequestBody.create(body.toString(),
                            okhttp3.MediaType.parse("application/json")))
                    .build();

            try (Response httpResponse = httpClient.newCall(request).execute()) {
                if (!httpResponse.isSuccessful()) {
                    logger.error("Send plant menu follow-up failed: {}", httpResponse.code());
                }
            }
        } catch (Exception e) {
            logger.error("Error sending plant menu follow-up", e);
        }
    }

    /**
     * Create plant menu components (action rows with buttons)
     */
    private ArrayNode createPlantMenuComponents() {
        ArrayNode components = objectMapper.createArrayNode();

        // Row 1: Fast growing crops
        components.add(createActionRow(
                createButton("paddy", "🌾 ข้าว", 1),
                createButton("tomato", "🍅 มะเขือเทศ", 1),
                createButton("corn", "🌽 ข้าวโพด", 1),
                createButton("pineapple", "🍍 สับปะรด", 1),
                createButton("tea", "🍃 ชา", 1)
        ));

        // Row 2: Medium crops
        components.add(createActionRow(
                createButton("wheat", "🌾 ข้าวสาลี", 1),
                createButton("potato", "🥔 มันฝรั่ง", 1),
                createButton("carrot", "🥕 แครอท", 1),
                createButton("lettuce", "🥬 ผักกาด", 1)
        ));

        // Row 3: Long crops
        components.add(createActionRow(
                createButton("grape", "🍇 องุ่น", 1),
                createButton("strawberry", "🍓 สตรอว์เบอร์รี่", 1),
                createButton("avocado", "🥑 อะโวคาโด", 1),
                createButton("cacao", "🍫 โกโก้", 1)
        ));

        return components;
    }

    private ObjectNode createActionRow(ObjectNode... buttons) {
        ObjectNode row = objectMapper.createObjectNode();
        row.put("type", 1);
        ArrayNode componentsArray = row.putArray("components");
        for (ObjectNode button : buttons) {
            componentsArray.add(button);
        }
        return row;
    }

    private ObjectNode createButton(String customId, String label, int style) {
        ObjectNode button = objectMapper.createObjectNode();
        button.put("type", 2);
        button.put("style", style);
        button.put("custom_id", customId);
        button.put("label", label);
        return button;
    }

    /**
     * Save crop to database and schedule notifications
     */
    private void saveCropAndSchedule(String userId, String channelId, String cropName) {
        int growTime = getGrowTime(cropName);
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        ZonedDateTime harvestTime = now.plusMinutes(growTime);
        ZonedDateTime earlyTime = harvestTime.minusMinutes(5);

        Crop crop = new Crop();
        crop.setUserId(userId);
        crop.setCropName(cropName);
        crop.setPlantTime(now.toLocalDateTime());
        crop.setHarvestTime(harvestTime.toLocalDateTime());
        crop.setNotifyEarly(true);
        crop.setType("crop");

        cropRepository.save(crop);

        // Schedule notifications
        schedulerService.scheduleNotification(userId, channelId, cropName,
                harvestTime.toLocalDateTime(), earlyTime.toLocalDateTime());
    }

    /**
     * Schedule reconnect with delay
     */
    private void scheduleReconnect(long delay) {
        try {
            Thread.sleep(delay);
            if (!isConnected) {
                logger.info("Attempting to reconnect...");
                connect();
            }
        } catch (InterruptedException e) {
            logger.info("Reconnect interrupted");
        }
    }

    /**
     * Close WebSocket connection
     */
    @PreDestroy
    public void disconnect() {
        logger.info("Disconnecting from Discord Gateway...");
        isConnected = false;
        if (webSocket != null) {
            webSocket.close(1000, "Application shutting down");
        }
    }

    private boolean isValidCrop(String crop) {
        return switch (crop.toLowerCase()) {
            case "tomato", "pineapple", "paddy", "potato", "carrot", "wheat", "strawberry", "eggplant", "lettuce",
                    "grape", "corn", "tea", "cacao", "avocado" -> true;
            default -> false;
        };
    }

    private int getGrowTime(String crop) {
        return switch (crop.toLowerCase()) {
            case "tomato" -> 15;
            case "paddy" -> 20;
            case "pineapple" -> 30;
            case "tea" -> 45;
            case "potato" -> 60;
            case "carrot" -> 120;
            case "wheat" -> 240;
            case "cacao" -> 300;
            case "strawberry" -> 360;
            case "eggplant" -> 420;
            case "lettuce" -> 480;
            case "grape" -> 600;
            case "corn" -> 720;
            case "avocado" -> 840;
            default -> 0;
        };
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
