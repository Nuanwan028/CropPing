package cropping.service;

import org.springframework.stereotype.Service;
import cropping.entity.Crop;
import cropping.repository.CropRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class CropService {

    private final CropRepository repo;
    private final SchedulerService scheduler;
    private final LineService lineService;

    public static final ZoneId ZONE = ZoneId.of("Asia/Bangkok");

    public CropService(CropRepository repo, SchedulerService scheduler, LineService lineService) {
        this.repo = repo;
        this.scheduler = scheduler;
        this.lineService = lineService;
    }

    private boolean isValidCrop(String crop) {
        return switch (crop.toLowerCase()) {
            case "tomato", "pineapple", "paddy", "potato", "carrot", "wheat", "strawberry", "eggplant", "lettuce",
                    "grape", "corn", "tea", "cacao", "avocado" ->
                true;
            default -> false;
        };
    }

    public void handleUserMessage(String userId, String message, String replyToken) {

        if (!isValidCrop(message)) {
            lineService.reply(replyToken, "❌ ไม่รู้จักพืชนี้");
            return;
        }

        int growTime = getGrowTime(message); // นาที

        ZonedDateTime now = ZonedDateTime.now(ZONE);
        ZonedDateTime harvestTime = now.plusMinutes(growTime);
        ZonedDateTime earlyTime = harvestTime.minusMinutes(5);

        Crop crop = new Crop();
        crop.setUserId(userId);
        crop.setCropName(message);
        crop.setPlantTime(now.toLocalDateTime());
        crop.setHarvestTime(harvestTime.toLocalDateTime());
        crop.setNotifyEarly(true);
        crop.setType("crop");

        repo.save(crop);

        // schedule
        scheduler.scheduleNotification(userId, message, harvestTime.toLocalDateTime(), earlyTime.toLocalDateTime());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM HH:mm");

        // reply
        String cropDisplay = getCropDisplayName(message);
        lineService.reply(replyToken,
                "🌱 " + cropDisplay + " ปลูกแล้ว!\n⏰ จะโตตอน " + harvestTime.format(formatter));
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

    private int getGrowTime(String crop) {
        return switch (crop.toLowerCase()) {
            case "tomato" -> 15;
            case "paddy" -> 20;
            case "pineapple" -> 30;
            case "tea" -> 45;
            case "potato" -> 60;
            case "carrot" -> 120; // 2 hr
            case "wheat" -> 240; // 4 hr
            case "cacao" -> 300; // 5 hr
            case "strawberry" -> 360; // 6 hr
            case "eggplant" -> 420; // 7 hr
            case "lettuce" -> 480; // 8 hr
            case "grape" -> 600; // 10 hr
            case "corn" -> 720; // 12 hr
            case "avocado" -> 840; // 14 hr
            default -> 0;
        };
    }

    public void handleList(String userId, String replyToken) {

        var crops = repo.findByUserId(userId);
        ZonedDateTime now = ZonedDateTime.now(ZONE);

        // กรองพืชที่ยังไม่โต (เวลาเก็บเกี่ยวยังไม่ถึง)
        var activeCrops = crops.stream()
                .filter(c -> c.getHarvestTime().isAfter(now.toLocalDateTime()))
                .toList();

        // ลบพืชที่โตเกินเวลาแล้ว
        var expiredCrops = crops.stream()
                .filter(c -> !c.getHarvestTime().isAfter(now.toLocalDateTime()))
                .toList();

        if (!expiredCrops.isEmpty()) {
            repo.deleteAll(expiredCrops);
        }

        if (activeCrops.isEmpty()) {
            lineService.reply(replyToken, "📭 ไม่มีพืชที่ปลูกอยู่");
            return;
        }

        lineService.replyCropList(replyToken, activeCrops);
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

}