package cropping.dto;

import lombok.Data;

@Data
public class Event {
    private String type;
    private String replyToken;
    private Source source;
    private Message message;
    private long timestamp;
}