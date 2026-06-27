package cropping.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health check endpoint for deployment monitoring
 */
@RestController
public class HealthController {

    @GetMapping("/ping")
    public String ping() {
        return "OK";
    }
}
