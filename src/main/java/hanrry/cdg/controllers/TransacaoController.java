package hanrry.cdg.controllers;

import hanrry.cdg.dtos.DashboardResponseDTO;
import hanrry.cdg.dtos.TransacaoRequestDTO;
import hanrry.cdg.dtos.TransacaoResponseDTO;
import hanrry.cdg.services.TransacaoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/transacoes")
public class TransacaoController {

    private final TransacaoService service;

    public TransacaoController(TransacaoService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TransacaoResponseDTO> criar(@RequestBody TransacaoRequestDTO dto, UriComponentsBuilder uriBuilder) {
        TransacaoResponseDTO resposta = service.criar(dto);
        URI uri = uriBuilder.path("/transacoes/{id}").buildAndExpand(resposta.getId()).toUri();
        return ResponseEntity.created(uri).body(resposta);
    }

    @GetMapping
    public ResponseEntity<List<TransacaoResponseDTO>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponseDTO> getDashboard() {
        return ResponseEntity.ok(service.getDashboard());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        service.deletar(id);
        return ResponseEntity.noContent().build();
    }
}