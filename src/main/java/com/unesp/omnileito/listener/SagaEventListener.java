package com.unesp.omnileito.listener;

import com.unesp.omnileito.configuration.RabbitMQConfig;
import com.unesp.omnileito.domain.enums.StatusAmbulancia;
import com.unesp.omnileito.domain.enums.StatusLeito;
import com.unesp.omnileito.dto.AlocacaoAmbulanciaEvent;
import com.unesp.omnileito.dto.DespachoRequestDTO;
import com.unesp.omnileito.dto.ReservaLeitoEvent;
import com.unesp.omnileito.repository.AmbulanciaRepository;
import com.unesp.omnileito.repository.LeitoRepository;
import com.unesp.omnileito.service.SagaOrchestratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SagaEventListener {

    private final SagaOrchestratorService sagaOrchestratorService;
    private final AmbulanciaRepository ambulanciaRepository;
    private final LeitoRepository leitoRepository;

    // Escuta requisição de alocação de ambulância
    @RabbitListener(queues = RabbitMQConfig.QUEUE_ALOCAR_AMBULANCIA)
    public void handleAlocarAmbulancia(DespachoRequestDTO dto) {
        // Busca primeira ambulância DISPONÍVEL
        var ambulanciaOpt = ambulanciaRepository.findFirstByStatus(StatusAmbulancia.DISPONIVEL);

        AlocacaoAmbulanciaEvent event = new AlocacaoAmbulanciaEvent();
        event.setOcorrenciaId(dto.getOcorrenciaId());
        event.setTipoLeitoNecessario(dto.getTipoLeitoNecessario());

        if (ambulanciaOpt.isPresent()) {
            var ambulancia = ambulanciaOpt.get();
            ambulancia.setStatus(StatusAmbulancia.ALOCADA);
            ambulanciaRepository.save(ambulancia);

            event.setAmbulanciaId(ambulancia.getId());
            event.setSucesso(true);
        } else {
            event.setSucesso(false);
            event.setMensagemErro("Nenhuma ambulância disponível no momento");
        }

        // Devolve o resultado ao Orquestrador
        sagaOrchestratorService.processarResultadoAmbulancia(event);
    }

    // Escuta requisição de reserva de leito
    @RabbitListener(queues = RabbitMQConfig.QUEUE_RESERVAR_LEITO)
    public void handleReservarLeito(AlocacaoAmbulanciaEvent eventAmbulancia) {
        // Busca primeiro leito LIVRE
        var leitoOpt = leitoRepository.findFirstByStatus(StatusLeito.LIVRE);

        ReservaLeitoEvent event = new ReservaLeitoEvent();
        event.setOcorrenciaId(eventAmbulancia.getOcorrenciaId());
        event.setAmbulanciaId(eventAmbulancia.getAmbulanciaId());

        if (leitoOpt.isPresent()) {
            var leito = leitoOpt.get();
            leito.setStatus(StatusLeito.RESERVADO);
            leitoRepository.save(leito);

            event.setLeitoId(leito.getId());
            event.setSucesso(true);
        } else {
            event.setSucesso(false);
            event.setMensagemErro("Nenhum leito disponível no hospital");
        }

        // Devolve o resultado ao Orquestrador
        sagaOrchestratorService.processarResultadoLeito(event);
    }

    // Escuta AÇÃO DE COMPENSAÇÃO: Liberar Ambulância em caso de falha no leito
    @RabbitListener(queues = RabbitMQConfig.QUEUE_LIBERAR_AMBULANCIA)
    public void handleLiberarAmbulancia(Long ambulanciaId) {
        if (ambulanciaId != null) {
            ambulanciaRepository.findById(ambulanciaId).ifPresent(amb -> {
                amb.setStatus(StatusAmbulancia.DISPONIVEL);
                ambulanciaRepository.save(amb);
            });
        }
    }
}