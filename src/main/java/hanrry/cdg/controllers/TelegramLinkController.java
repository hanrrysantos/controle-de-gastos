package hanrry.cdg.controllers;

import hanrry.cdg.dtos.TelegramLinkResponseDTO;
import hanrry.cdg.services.TelegramLinkService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/integracoes/telegram")
public class TelegramLinkController {

    private final TelegramLinkService service;

    public TelegramLinkController(TelegramLinkService service) {
        this.service = service;
    }

    @GetMapping("/vinculo")
    public ResponseEntity<Map<String, Boolean>> consultarVinculo() {
        return ResponseEntity.ok(Map.of("vinculado", service.estaVinculado()));
    }

    @PostMapping("/vinculo")
    public ResponseEntity<TelegramLinkResponseDTO> gerarCodigo() {
        return ResponseEntity.ok(service.gerarCodigo());
    }
}
