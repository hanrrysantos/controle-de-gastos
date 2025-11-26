package hanrry.cdg.controllers;

import hanrry.cdg.dtos.CategoriaCreateDTO;
import hanrry.cdg.dtos.CategoriaResponseDTO;
import hanrry.cdg.services.CategoriaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/categorias")
public class CategoriaController {

    private final CategoriaService service;

    public CategoriaController(CategoriaService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CategoriaResponseDTO> criar(@RequestBody CategoriaCreateDTO dto, UriComponentsBuilder uriBuilder) {
        CategoriaResponseDTO novaCategoria = service.criar(dto);

        URI uri = uriBuilder.path("/categorias/{id}").buildAndExpand(novaCategoria.getId()).toUri();

        return ResponseEntity.created(uri).body(novaCategoria);
    }

    @GetMapping
    public ResponseEntity<List<CategoriaResponseDTO>> listar() {
        List<CategoriaResponseDTO> categorias = service.listar();
        return ResponseEntity.ok(categorias);
    }
}