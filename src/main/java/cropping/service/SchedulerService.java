package cropping.service;

import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Service
public class SchedulerService {

    private final TaskScheduler taskScheduler;
    private final LineService lineService;
    private final DiscordService discordService;

    private static final ZoneId ZONE = ZoneId.of("Asia/Bangkok");

    public SchedulerService(TaskScheduler taskScheduler, LineService lineService, DiscordService discordService) {
        this.taskScheduler = taskScheduler;
        this.lineService = lineService;
        this.discordService = discordService;
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

    public void scheduleNotification(String userId, String channelId, String crop,
                                     LocalDateTime harvest, LocalDateTime early) {

        String cropDisplay = getCropDisplayName(crop);

        // เตือนก่อน (5 นาที)
        taskScheduler.schedule(() -> {
            // LINE notification
            lineService.push(userId, "⏰ อีก 5 นาที " + cropDisplay + " จะโต!");

            // Discord notification (ถ้ามี channelId)
            if (channelId != null && !channelId.isEmpty()) {
                discordService.sendMessage(channelId, "⏰ อีก 5 นาที " + cropDisplay + " จะโต!");
            }
        }, Date.from(early.atZone(ZONE).toInstant()));

        // เตือนจริง (ถึงเวลาเก็บเกี่ยว)
        taskScheduler.schedule(() -> {
            // LINE notification
            lineService.push(userId, "🌱 " + cropDisplay + " โตแล้ว! เก็บเกี่ยวได้แล้ว!");

            // Discord notification (ถ้ามี channelId)
            if (channelId != null && !channelId.isEmpty()) {
                discordService.sendMessage(channelId, "🌱 " + cropDisplay + " โตแล้ว! เก็บเกี่ยวได้แล้ว!");
            }
        }, Date.from(harvest.atZone(ZONE).toInstant()));
    }
}