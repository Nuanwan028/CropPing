package cropping.dto;

import lombok.Data;

@Data
public class Source {
    private String type;   // user, group, room
    private String userId;
    private String groupId;
    private String roomId;
}

