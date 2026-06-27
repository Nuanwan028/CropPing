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
    private final DiscordService discordService;

    public static final ZoneId ZONE = ZoneId.of("Asia/Bangkok");

    public CropService(CropRepository repo, SchedulerService scheduler,
                      LineService lineService, DiscordService discordService) {
        this.repo = repo;
        this.scheduler = scheduler;
        this.lineService = lineService;
        this.discordService = discordService;
    }

    private boolean isValidCrop(String crop) {
        return switch (crop.toLowerCase()) {
            case "tomato", "pineapple", "paddy", "potato", "carrot", "wheat", "strawberry", "eggplant", "lettuce",
                    "grape", "corn", "tea", "cacao", "avocado" ->
                true;
            default -> false;
        };
    }

    /**
     * จัดการเมื่อ user สั่งปลูกพืช (รองรับทั้ง LINE และ Discord)
     *
     * @param userId ID ของ user
     * @param message ชื่อพืช
     * @param replyToken LINE replyToken (ถ้าเป็น LINE) หรือ Discord channelId (ถ้าเป็น Discord)
     * @param platform "LINE" หรือ "DISCORD"
     */
    public void handleUserMessage(String userId, String message, String replyToken, String platform) {

        if (!isValidCrop(message)) {
            if ("LINE".equals(platform)) {
                lineService.reply(replyToken, "❌ ไม่รู้จักพืชนี้");
            } else {
                discordService.sendMessage(replyToken, "❌ ไม่รู้จักพืชนี้");
            }
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

        // schedule notification
        scheduler.scheduleNotification(userId, replyToken, message, harvestTime.toLocalDateTime(), earlyTime.toLocalDateTime());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM HH:mm");

        // reply
        String cropDisplay = getCropDisplayName(message);
        String harvestTimeStr = harvestTime.format(formatter);

        if ("LINE".equals(platform)) {
            lineService.reply(replyToken,
                    "🌱 " + cropDisplay + " ปลูกแล้ว!\n⏰ จะโตตอน " + harvestTimeStr);
        } else {
            discordService.sendPlantSuccess(replyToken, message, harvestTimeStr);
        }
    }

    /**
     * Overload method - สำหรับ BACKWARD COMPATIBILITY (LINE เดิม)
     */
    public void handleUserMessageLine(String userId, String message, String replyToken) {
        handleUserMessage(userId, message, replyToken, "LINE");
    }

    /**
     * Overload method - สำหรับ Discord
     */
    public void handleUserMessage(String userId, String message, String channelId) {
        handleUserMessage(userId, message, channelId, "DISCORD");
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

    /**
     * ดูรายการพืชทั้งหมด (รองรับทั้ง LINE และ Discord)
     */
    public void handleList(String userId, String targetId, String platform) {

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
            if ("LINE".equals(platform)) {
                lineService.reply(targetId, "📭 ไม่มีพืชที่ปลูกอยู่");
            } else {
                discordService.sendMessage(targetId, "📭 ไม่มีพืชที่ปลูกอยู่");
            }
            return;
        }

        if ("LINE".equals(platform)) {
            lineService.replyCropList(targetId, activeCrops);
        } else {
            discordService.sendCropList(targetId, activeCrops);
        }
    }

    /**
     * Overload - สำหรับ LINE (backward compatibility)
     */
    public void handleList(String userId, String replyToken) {
        handleList(userId, replyToken, "LINE");
    }

    /**
     * ยกเลิกพืช (รองรับทั้ง LINE และ Discord)
     */
    public void cancelCrop(Long id, String targetId, String platform) {
        repo.deleteById(id);
        if ("LINE".equals(platform)) {
            lineService.reply(targetId, "❌ ยกเลิกเรียบร้อย");
        } else {
            discordService.sendCropCancelled(targetId, "", String.valueOf(id));
        }
    }

    /**
     * Overload - สำหรับ LINE (backward compatibility)
     */
    public void cancelCrop(Long id, String replyToken) {
        cancelCrop(id, replyToken, "LINE");
    }

    /**
     * ยกเลิกทั้งหมด (รองรับทั้ง LINE และ Discord)
     */
    public void cancelAll(String userId, String targetId, String platform) {
        var crops = repo.findByUserId(userId);
        repo.deleteAll(crops);
        if ("LINE".equals(platform)) {
            lineService.reply(targetId, "🗑 ลบทั้งหมดแล้ว");
        } else {
            discordService.sendMessage(targetId, "🗑 ลบทั้งหมดแล้ว");
        }
    }

    /**
     * Overload - สำหรับ LINE (backward compatibility)
     */
    public void cancelAll(String userId, String replyToken) {
        cancelAll(userId, replyToken, "LINE");
    }

}