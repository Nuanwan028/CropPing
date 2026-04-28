package cropping.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LineConfig {

    @Value("${line.channel.token}")
    private String channelToken;

    @Value("${line.channel.secret}")
    private String channelSecret;
}