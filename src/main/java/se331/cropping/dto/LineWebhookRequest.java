package se331.cropping.dto;

import lombok.Data;

import java.awt.*;
import java.util.List;

@Data
public class LineWebhookRequest {
    private List<Event> events;
}