package cropping.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

/**
 * Discord Gateway Service - เชื่อมต่อ Discord ผ่าน WebSocket
 * Slash Commands จะส่งมาทาง Gateway แทน HTTP Endpoint
 */
@Service
public class DiscordGatewayService {

    private static final Logger logger = LoggerFactory.getLogger(DiscordGatewayService.class);
    private static final String GATEWAY_URL = "wss://gateway.discord.gg";

    @Value("${discord.bot.token}")
    private String botToken;

    @Value("${discord.application.id}")
    private String applicationId;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final CropService cropService;
    private final DiscordService discordService;

    private WebSocket webSocket;
    private volatile boolean isConnected = false;
    private volatile int heartbeatInterval = 41250; // default ~41 seconds
    private volatile long lastHeartbeatAck = 0;
    private volatile String sessionId = null;
    private volatile int sequenceNumber = 0;

    public DiscordGatewayService(CropService cropService, DiscordService discordService) {
        this.cropService = cropService;
        this.discordService = discordService;
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
            sendInteractionResponse(token, createPongResponse());
            return;
        }

        // Type 2: APPLICATION_COMMAND
        if (type == 2 && data.has("data")) {
            JsonNode dataNode = data.get("data");
            String commandName = dataNode.get("name").asText();

            logger.info("Processing command: {} synchronously", commandName);

            // Process command synchronously and send immediate response
            String resultMessage = processCommandSync(commandName, userId, channelId, dataNode);
            sendInteractionResponse(token, createMessageResponse(resultMessage));
            return;
        }

        // Type 3: MESSAGE_COMPONENT (Button)
        if (type == 3) {
            JsonNode dataNode = data.get("data");
            String customId = dataNode.get("custom_id").asText();

            logger.info("Processing button click: {} synchronously", customId);

            // Process button click synchronously and send immediate response
            String resultMessage = processButtonClickSync(customId, userId, channelId);
            sendInteractionResponse(token, createMessageResponse(resultMessage));
            return;
        }
    }

    /**
     * Process command asynchronously and edit the original response
     */
    @Async
    protected void processCommandAsync(String commandName, String userId, String channelId, JsonNode data, String token) {
        try {
            logger.info("Async processing command: {} started", commandName);
            String resultMessage;
            switch (commandName) {
                case "plant":
                    resultMessage = handlePlantCommandAsync(userId, channelId, data);
                    break;
                case "list":
                    resultMessage = handleListCommandAsync(userId, channelId);
                    break;
                case "cancel":
                    resultMessage = handleCancelCommandAsync(userId, channelId, data);
                    break;
                case "cancel_all":
                    resultMessage = handleCancelAllCommandAsync(userId, channelId);
                    break;
                default:
                    resultMessage = "❌ ไม่รู้จักคำสั่งนี้";
            }
            logger.info("Async processing command: {} completed, editing response", commandName);
            // Edit the original response instead of sending follow-up
            editOriginalResponse(token, resultMessage);
        } catch (Exception e) {
            logger.error("Error processing command: {}", commandName, e);
            editOriginalResponse(token, "❌ เกิดข้อผิดพลาด");
        }
    }

    /**
     * Process button click asynchronously and edit the original response
     */
    @Async
    protected void processButtonClickAsync(String customId, String userId, String channelId, String token) {
        try {
            cropService.handleUserMessage(userId, customId, channelId, "DISCORD");
            editOriginalResponse(token, "✅ กำลังปลูก " + getCropDisplayName(customId) + "...");
        } catch (Exception e) {
            logger.error("Error processing button click", e);
            editOriginalResponse(token, "❌ เกิดข้อผิดพลาด");
        }
    }

    /**
     * Process command synchronously and return response message
     */
    private String processCommandSync(String commandName, String userId, String channelId, JsonNode data) {
        try {
            switch (commandName) {
                case "plant":
                    return handlePlantCommandAsync(userId, channelId, data);
                case "list":
                    return handleListCommandAsync(userId, channelId);
                case "cancel":
                    return handleCancelCommandAsync(userId, channelId, data);
                case "cancel_all":
                    return handleCancelAllCommandAsync(userId, channelId);
                default:
                    return "❌ ไม่รู้จักคำสั่งนี้";
            }
        } catch (Exception e) {
            logger.error("Error processing command: {}", commandName, e);
            return "❌ เกิดข้อผิดพลาด";
        }
    }

    /**
     * Process button click synchronously and return response message
     */
    private String processButtonClickSync(String customId, String userId, String channelId) {
        try {
            cropService.handleUserMessage(userId, customId, channelId, "DISCORD");
            return "✅ กำลังปลูก " + getCropDisplayName(customId) + "...";
        } catch (Exception e) {
            logger.error("Error processing button click", e);
            return "❌ เกิดข้อผิดพลาด";
        }
    }

    private String handlePlantCommandAsync(String userId, String channelId, JsonNode data) {
        if (data.has("options") && data.get("options").size() > 0) {
            String cropName = data.get("options").get(0).get("value").asText();
            cropService.handleUserMessage(userId, cropName, channelId, "DISCORD");
            return "✅ กำลังปลูก...";
        } else {
            discordService.sendPlantMenu(channelId);
            return "🌱 เลือกพืชที่จะปลูก:";
        }
    }

    private String handleListCommandAsync(String userId, String channelId) {
        cropService.handleList(userId, channelId, "DISCORD");
        return "📋 ดึงรายการพืชเรียบร้อย";
    }

    private String handleCancelCommandAsync(String userId, String channelId, JsonNode data) {
        if (data.has("options") && data.get("options").size() > 0) {
            Long id = data.get("options").get(0).get("value").asLong();
            cropService.cancelCrop(id, channelId, "DISCORD");
            return "❌ ยกเลิกเรียบร้อย";
        } else {
            return "❌ กรุณาระบุ ID พืช";
        }
    }

    private String handleCancelAllCommandAsync(String userId, String channelId) {
        cropService.cancelAll(userId, channelId, "DISCORD");
        return "🗑️ ลบทั้งหมดแล้ว";
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
    private void sendInteractionResponse(String interactionToken, ObjectNode response) {
        try {
            // Correct URL format: /interactions/{applicationId}/{interactionToken}/callback
            String url = "https://discord.com/api/v10/interactions/" + applicationId + "/" + interactionToken + "/callback";
            logger.debug("Sending interaction response to: {}", url);
            logger.debug("Response body: {}", response.toString());

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bot " + botToken)
                    .addHeader("Content-Type", "application/json")
                    .post(okhttp3.RequestBody.create(response.toString(),
                            okhttp3.MediaType.parse("application/json")))
                    .build();

            try (Response httpResponse = httpClient.newCall(request).execute()) {
                if (!httpResponse.isSuccessful()) {
                    String errorBody = httpResponse.body() != null ? httpResponse.body().string() : "no body";
                    logger.error("Interaction response failed: {} - URL: {} - Error: {}", httpResponse.code(), url, errorBody);
                } else {
                    logger.debug("Interaction response successful");
                }
            }
        } catch (Exception e) {
            logger.error("Error sending interaction response", e);
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
