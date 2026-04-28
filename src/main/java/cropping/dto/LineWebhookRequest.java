package cropping.dto;

import lombok.Data;

import java.util.List;

@Data
public class LineWebhookRequest {
    private List<Event> events;
}