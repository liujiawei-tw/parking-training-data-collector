package tw.parking.collector;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class CollectorApplication {
    public static void main(String[] args) throws Exception {
        Files.createDirectories(Path.of("data"));
        SpringApplication.run(CollectorApplication.class, args);
    }
}
