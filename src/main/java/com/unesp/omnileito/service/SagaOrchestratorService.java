package com.unesp.omnileito.service;

import com.unesp.omnileito.configuration.RabbitMQConfig;
import com.unesp.omnileito.domain.Ocorrencia;
import com.unesp.omnileito.domain.enums.StatusOcorrencia;
import com.unesp.omnileito.dto.AlocacaoAmbulanciaEvent;
import com.unesp.omnileito.dto.DespachoRequestDTO;
import com.unesp.omnileito.dto.ReservaLeitoEvent;
import com.unesp.omnileito.repository.OcorrenciaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SagaOrchestratorService {

    private final RabbitTemplate rabbitTemplate;
    private final OcorrenciaRepository ocorrenciaRepository;

    @Transactional
    public void iniciarDespacho(DespachoRequestDTO dto) {
        // Se a ocorrência não existir no banco, cria e inicializa a entidade
        Ocorrencia ocorrencia = ocorrenciaRepository.findById(dto.getOcorrenciaId())
                .orElseGet(() -> Ocorrencia.builder()
                        .id(dto.getOcorrenciaId())
                        .tipoLeitoNecessario(dto.getTipoLeitoNecessario())
                        .build());

        ocorrencia.setStatus(StatusOcorrencia.EM_DESPACHO);
        ocorrenciaRepository.save(ocorrencia);

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_SAGA,
                RabbitMQConfig.ROUTING_ALOCAR_AMBULANCIA,
                dto
        );
    }

    public void processarResultadoAmbulancia(AlocacaoAmbulanciaEvent event) {
        if (event.isSucesso()) {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_SAGA,
                    RabbitMQConfig.ROUTING_RESERVAR_LEITO,
                    event
            );
        } else {
            executarCompensacao(event.getOcorrenciaId(), "FALHA_ALOCACAO_AMBULANCIA");
        }
    }

    @Transactional
    public void processarResultadoLeito(ReservaLeitoEvent event) {
        if (event.isSucesso()) {
            // Atualiza a ocorrência com status DESPACHADA e salva os IDs dos recursos
            ocorrenciaRepository.findById(event.getOcorrenciaId()).ifPresent(ocorrencia -> {
                ocorrencia.setStatus(StatusOcorrencia.DESPACHADA);
                ocorrencia.setAmbulanciaAlocadaId(event.getAmbulanciaId());
                ocorrencia.setLeitoReservadoId(event.getLeitoId());
                ocorrenciaRepository.save(ocorrencia);
            });
        } else {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_SAGA,
                    RabbitMQConfig.ROUTING_LIBERAR_AMBULANCIA,
                    event.getAmbulanciaId()
            );
            executarCompensacao(event.getOcorrenciaId(), "FALHA_RESERVA_LEITO");
        }
    }

    private void executarCompensacao(String ocorrenciaId, String motivo) {
        ocorrenciaRepository.findById(ocorrenciaId).ifPresent(ocorrencia -> {
            ocorrencia.setStatus(StatusOcorrencia.FALHA_DESPACHO);
            ocorrenciaRepository.save(ocorrencia);
        });
    }
}