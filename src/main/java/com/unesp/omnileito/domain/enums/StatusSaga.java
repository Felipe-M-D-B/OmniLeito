package com.unesp.omnileito.domain.enums;

public enum StatusSaga {
    //orquestrador recebeu a requisicao e publicou o evento na fila para buscar uma ambulancia
    INICIADA,
    //A etapa de alocacao da ambulancia foi concluida com sucesso no listener
    AMBULANCIA_ALOCADA,
    //O leito necessario foi encontrado e reservado com sucesso.
    LEITO_RESERVADO,
    //Todos os passos foram finalizados, final feliz
    CONCLUIDA,
    //Houve uma falha (ausencia de leito) e o saga esta liberando os recursos
    EM_COMPENSACAO,
    //Rollback finalizado, a ambulancia esta disponivel novamente por exemplo
    COMPENSADA,
    //Nao foi capaz de concluir o fluxo, final triste
    FALHADA
}
