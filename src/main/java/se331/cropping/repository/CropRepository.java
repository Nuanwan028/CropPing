package se331.cropping.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se331.cropping.entity.Crop;

import java.util.List;

public interface CropRepository extends JpaRepository<Crop, Long> {
    List<Crop> findByUserId(String userId);
}