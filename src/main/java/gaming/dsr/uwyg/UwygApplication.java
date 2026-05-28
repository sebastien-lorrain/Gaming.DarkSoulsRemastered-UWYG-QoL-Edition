package gaming.dsr.uwyg;

import org.jspecify.annotations.NonNull;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class UwygApplication implements CommandLineRunner {

    private final UwygOrchestrator uwygOrchestrator;

    public UwygApplication(final UwygOrchestrator uwygOrchestrator) {
        this.uwygOrchestrator = uwygOrchestrator;
    }

    public static void main(final String[] commandLineArguments) {
        SpringApplication.run(UwygApplication.class, commandLineArguments);
    }

    @Override
    public void run(final String @NonNull ... commandLineArguments) {
        uwygOrchestrator.runForever();
    }
}
