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

    private static final ZoneId ZONE = ZoneId.of("Asia/Bangkok");

    public SchedulerService(TaskScheduler taskScheduler, LineService lineService) {
        this.taskScheduler = taskScheduler;
        this.lineService = lineService;
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

    public void scheduleNotification(String userId, String crop,
                                     LocalDateTime harvest, LocalDateTime early) {

        String cropDisplay = getCropDisplayName(crop);

        // เตือนก่อน
        taskScheduler.schedule(() -> {
            lineService.push(userId, "⏰ อีก 5 นาที " + cropDisplay + " จะโต!");
        }, Date.from(early.atZone(ZONE).toInstant()));

        // เตือนจริง
        taskScheduler.schedule(() -> {
            lineService.push(userId, "🌱 " + cropDisplay + " โตแล้ว!");
        }, Date.from(harvest.atZone(ZONE).toInstant()));
    }
}