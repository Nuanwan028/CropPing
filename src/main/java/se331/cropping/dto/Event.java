package se331.cropping.dto;

import lombok.Data;
import se331.cropping.dto.Source;

@Data
public class Event {
    private String type;
    private String replyToken;
    private Source source;
    private Message message;
    private long timestamp;
}