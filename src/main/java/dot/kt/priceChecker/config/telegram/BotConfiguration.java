package dot.kt.priceChecker.config.telegram;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({TelegramProperties.class, OllamaAssistantProperties.class})
public class BotConfiguration {
    @Bean @ConditionalOnMissingBean(Clock.class)
    Clock applicationClock() { return Clock.systemUTC(); }
    @Bean
    ThreadPoolTaskScheduler taskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4); scheduler.setThreadNamePrefix("checker-jobs-");
        return scheduler;
    }
}
