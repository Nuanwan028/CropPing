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

    public SchedulerService(TaskScheduler taskScheduler, LineService lineService) {
        this.taskScheduler = taskScheduler;
        this.lineService = lineService;
    }

    public void scheduleNotification(String userId, String crop,
                                     LocalDateTime harvest, LocalDateTime early) {

        // เตือนก่อน
        taskScheduler.schedule(() -> {
            lineService.push(userId, "⏰ อีก 5 นาที " + crop + " จะโต!");
        }, Date.from(early.atZone(ZoneId.systemDefault()).toInstant()));

        // เตือนจริง
        taskScheduler.schedule(() -> {
            lineService.push(userId, "🌱 " + crop + " โตแล้ว!");
        }, Date.from(harvest.atZone(ZoneId.systemDefault()).toInstant()));
    }
}