package cropping.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
public class Crop {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;
    private String cropName;

    private LocalDateTime plantTime;
    private LocalDateTime harvestTime;

    private boolean notifyEarly;

    private String type; // "crop" หรือ "reminder"

}