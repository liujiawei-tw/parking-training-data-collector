package tw.parking.collector;

import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CollectorApplication implements CommandLineRunner {
    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    private final TrainingDatasetService datasets;

    public CollectorApplication(TrainingDatasetService datasets) {
        this.datasets = datasets;
    }

    public static void main(String[] args) {
        SpringApplication.run(CollectorApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        String command = args.length == 0 ? "collect" : args[0];
        if ("collect".equals(command)) {
            datasets.collectOnce();
            return;
        }
        if ("export-daily".equals(command)) {
            LocalDate date = args.length >= 2 && !"yesterday".equals(args[1])
                    ? LocalDate.parse(args[1])
                    : LocalDate.now(TAIPEI).minusDays(1);
            datasets.exportDaily(date);
            return;
        }
        throw new IllegalArgumentException("Unknown command: " + command
                + ". Use collect or export-daily [yyyy-MM-dd|yesterday].");
    }
}
