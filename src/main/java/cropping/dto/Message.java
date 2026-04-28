package cropping.dto;

import lombok.Data;

@Data
public class Message {
    private String type; // text, image, sticker
    private String id;
    private String text;

}