package co.tecnosport.api.presentation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SaludControlador {

  @GetMapping("/api/v1/salud")
  public String salud() {
    return "OK";
  }
}
