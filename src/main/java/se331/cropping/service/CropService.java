package se331.cropping.service;

import org.springframework.stereotype.Service;
import se331.cropping.entity.Crop;
import se331.cropping.repository.CropRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class CropService {

    private final CropRepository repo;
    private final SchedulerService scheduler;
    private final LineService lineService;

    public CropService(CropRepository repo, SchedulerService scheduler, LineService lineService) {
        this.repo = repo;
        this.scheduler = scheduler;
        this.lineService = lineService;
    }

    private boolean isValidCrop(String crop) {
        return switch (crop.toLowerCase()) {
            case "tomato", "pineapple", "potato",
                 "carrot", "wheat", "strawberry",
                 "eggplant", "lettuce", "grape", "corn" -> true;
            default -> false;
        };
    }

    public void handleUserMessage(String userId, String message, String replyToken) {

        if (!isValidCrop(message)) {
            lineService.reply(replyToken, "❌ ไม่รู้จักพืชนี้");
            return;
        }

        int growTime = getGrowTime(message); // นาที

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime harvestTime = now.plusMinutes(growTime);
        LocalDateTime earlyTime = harvestTime.minusMinutes(5);

        Crop crop = new Crop();
        crop.setUserId(userId);
        crop.setCropName(message);
        crop.setPlantTime(now);
        crop.setHarvestTime(harvestTime);
        crop.setNotifyEarly(true);
        crop.setType("crop");

        repo.save(crop);

        // schedule
        scheduler.scheduleNotification(userId, message, harvestTime, earlyTime);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM HH:mm");

        // reply
        lineService.reply(replyToken,
                "🌱 " + message + " ปลูกแล้ว!\n⏰ จะโตตอน " + harvestTime.format(formatter));
    }

    private int getGrowTime(String crop) {
        return switch (crop.toLowerCase()) {
            case "tomato" -> 15;
            case "pineapple" -> 30;
            case "potato" -> 60;
            case "carrot" -> 120;    // 2 hr
            case "wheat" -> 240;     // 4 hr
            case "strawberry" -> 360; // 6 hr
            case "eggplant" -> 420;   // 7 hr
            case "lettuce" -> 480;    // 8 hr
            case "grape" -> 600;      // 10 hr
            case "corn" -> 720;       // 12 hr
            default -> 0;
        };
    }

    public void handleList(String userId, String replyToken) {

        var crops = repo.findByUserId(userId);

        if (crops.isEmpty()) {
            lineService.reply(replyToken, "📭 ไม่มีพืชที่ปลูกอยู่");
            return;
        }

        lineService.replyCropList(replyToken, crops);
    }

    public void cancelCrop(Long id, String replyToken) {
        repo.deleteById(id);
        lineService.reply(replyToken, "❌ ยกเลิกเรียบร้อย");
    }

    public void cancelAll(String userId, String replyToken) {
        var crops = repo.findByUserId(userId);
        repo.deleteAll(crops);
        lineService.reply(replyToken, "🗑 ลบทั้งหมดแล้ว");
    }

    public void createReminder(String userId, int hours, int minutes, String replyToken) {

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime notifyTime = now.plusHours(hours).plusMinutes(minutes);

        // ✅ สร้าง reminder
        Crop reminder = new Crop();
        reminder.setUserId(userId);
        reminder.setCropName("⏰ แจ้งเตือน");
        reminder.setPlantTime(now);
        reminder.setHarvestTime(notifyTime);
        reminder.setNotifyEarly(false);
        reminder.setType("reminder");

        repo.save(reminder); // ⭐ สำคัญ

        // schedule
        scheduler.scheduleNotification(
                userId,
                "⏰ แจ้งเตือนของคุณ",
                notifyTime,
                notifyTime
        );

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

        lineService.reply(replyToken,
                "✅ ตั้งแจ้งเตือนแล้ว!\n⏰ เวลา " + notifyTime.format(formatter));
    }

}