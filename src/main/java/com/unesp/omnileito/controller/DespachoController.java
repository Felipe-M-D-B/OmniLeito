package com.unesp.omnileito.controller;

import com.unesp.omnileito.dto.DespachoRequestDTO;
import com.unesp.omnileito.service.SagaOrchestratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/despachos")
@RequiredArgsConstructor
public class DespachoController {

    private final SagaOrchestratorService sagaOrchestratorService;

    @PostMapping
    public ResponseEntity<String> iniciarDespacho(@RequestBody DespachoRequestDTO dto) {
        sagaOrchestratorService.iniciarDespacho(dto);
        return ResponseEntity.ok("Saga de despacho iniciada para ocorrência: " + dto.getOcorrenciaId());
    }
}
