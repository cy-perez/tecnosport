package co.tecnosport.api.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "co.tecnosport.api")
public class TecnosportApiApplication {

  public static void main(String[] args) {
    SpringApplication.run(TecnosportApiApplication.class, args);
  }
}
