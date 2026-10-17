const estado = {
  token: sessionStorage.getItem("omnileitoToken") || "",
  prazo: null
};

const sessao = document.querySelector("#sessao");
const ocorrenciaId = document.querySelector("#ocorrencia-id");

function atualizarSessao() {
  sessao.textContent = estado.token
    ? "Token guardado nesta aba. As chamadas passam pelo gateway na porta 8085."
    : "Sem token. Entre para chamar a regulação.";
}

function mostrar(elemento, resposta) {
  const corpo = resposta.corpo == null ? "" : JSON.stringify(resposta.corpo, null, 2);
  elemento.textContent = "HTTP " + resposta.status + (corpo ? "\n" + corpo : "");
}

async function api(metodo, caminho, corpo) {
  const cabecalhos = {};
  if (corpo !== undefined) {
    cabecalhos["Content-Type"] = "application/json";
  }
  if (estado.token) {
    cabecalhos.Authorization = "Bearer " + estado.token;
  }
  try {
    const resposta = await fetch(caminho, {
      method: metodo,
      headers: cabecalhos,
      body: corpo === undefined ? undefined : JSON.stringify(corpo)
    });
    const texto = await resposta.text();
    let json = null;
    if (texto) {
      try {
        json = JSON.parse(texto);
      } catch (erro) {
        json = { mensagem: texto };
      }
    }
    return { status: resposta.status, corpo: json };
  } catch (erro) {
    return { status: 0, corpo: { codigo: "SEM_CONEXAO", mensagem: "O gateway não respondeu." } };
  }
}

function idAtual() {
  return ocorrenciaId.value.trim();
}

document.querySelector("#form-entrar").addEventListener("submit", async (evento) => {
  evento.preventDefault();
  const dados = new FormData(evento.target);
  const resposta = await api("POST", "/api/auth/login", {
    usuario: dados.get("usuario"),
    senha: dados.get("senha")
  });
  if (resposta.status === 200 && resposta.corpo && resposta.corpo.token) {
    estado.token = resposta.corpo.token;
    sessionStorage.setItem("omnileitoToken", estado.token);
    atualizarSessao();
  }
  mostrar(document.querySelector("#saida-entrar"), resposta);
  if (estado.token) {
    consultarModo();
  }
});

document.querySelector("#form-abrir").addEventListener("submit", async (evento) => {
  evento.preventDefault();
  const dados = new FormData(evento.target);
  const resposta = await api("POST", "/api/ocorrencias", {
    paciente: { nome: dados.get("nome") },
    local: dados.get("local"),
    prioridade: dados.get("prioridade"),
    tipoLeito: dados.get("tipoLeito")
  });
  if (resposta.corpo && resposta.corpo.id) {
    ocorrenciaId.value = resposta.corpo.id;
  }
  mostrar(document.querySelector("#saida-abrir"), resposta);
});

document.querySelector("#btn-ambulancias").addEventListener("click", async () => {
  mostrar(document.querySelector("#saida-ocupacao"), await api("GET", "/api/ambulancias/disponiveis"));
});

document.querySelector("#btn-leitos").addEventListener("click", async () => {
  const tipo = document.querySelector("#tipo-ocupacao").value;
  mostrar(document.querySelector("#saida-ocupacao"), await api("GET", "/api/leitos/disponiveis?tipoLeito=" + tipo));
});

async function consultarModo() {
  const resposta = await api("GET", "/api/leitos/demonstracao/resultado");
  const modo = resposta.corpo && resposta.corpo.resultado;
  document.querySelector("#modo-atual").textContent = resposta.status === 200
    ? (modo ? "Modo atual: " + modo : "Modo atual: aleatório")
    : "Não foi possível ler o modo. HTTP " + resposta.status;
}

document.querySelector("#modos").addEventListener("click", async (evento) => {
  const botao = evento.target.closest("button");
  if (!botao) {
    return;
  }
  const resposta = await api("PUT", "/api/leitos/demonstracao/resultado", { resultado: botao.dataset.resultado });
  if (resposta.status === 200) {
    const modo = resposta.corpo.resultado;
    document.querySelector("#modo-atual").textContent = modo ? "Modo atual: " + modo : "Modo atual: aleatório";
  } else {
    document.querySelector("#modo-atual").textContent = "HTTP " + resposta.status + " " + ((resposta.corpo && resposta.corpo.mensagem) || "");
  }
});

function texto(resposta) {
  const corpo = resposta.corpo == null ? "" : JSON.stringify(resposta.corpo, null, 2);
  return "HTTP " + resposta.status + (corpo ? "\n" + corpo : "");
}

async function acompanhar(id, destino, cabecalho) {
  const inicio = Date.now();
  while (Date.now() - inicio <= 15000) {
    const ultimo = await api("GET", "/api/ocorrencias/" + encodeURIComponent(id));
    destino.textContent = cabecalho + "\n\nAcompanhamento:\n" + texto(ultimo);
    await new Promise((resolver) => setTimeout(resolver, 2000));
  }
}

document.querySelector("#btn-despachar").addEventListener("click", async () => {
  const id = idAtual();
  const destino = document.querySelector("#saida-despacho");
  if (!id) {
    destino.textContent = "Abra uma ocorrência e use o id devolvido.";
    return;
  }
  const resposta = await api("POST", "/api/ocorrencias/" + encodeURIComponent(id) + "/despachos");
  mostrar(destino, resposta);
  await acompanhar(id, destino, texto(resposta));
});

document.querySelector("#btn-consultar").addEventListener("click", async () => {
  const id = idAtual();
  const destino = document.querySelector("#saida-despacho");
  if (!id) {
    destino.textContent = "Informe o id da ocorrência.";
    return;
  }
  mostrar(destino, await api("GET", "/api/ocorrencias/" + encodeURIComponent(id)));
});

document.querySelector("#btn-segundo").addEventListener("click", async () => {
  const id = idAtual();
  const destino = document.querySelector("#saida-segundo");
  if (!id) {
    destino.textContent = "Informe o id da ocorrência.";
    return;
  }
  mostrar(destino, await api("POST", "/api/ocorrencias/" + encodeURIComponent(id) + "/despachos"));
});

document.querySelector("#btn-notificacoes").addEventListener("click", async () => {
  const id = idAtual();
  const destino = document.querySelector("#saida-notificacoes");
  if (!id) {
    destino.textContent = "Informe o id da ocorrência.";
    return;
  }
  mostrar(destino, await api("GET", "/api/notificacoes?ocorrenciaId=" + encodeURIComponent(id)));
});

document.querySelector("#btn-prazo").addEventListener("click", () => {
  if (estado.prazo) {
    clearInterval(estado.prazo);
  }
  const fim = Date.now() + 120000;
  const relogio = document.querySelector("#relogio");
  estado.prazo = setInterval(async () => {
    const resta = fim - Date.now();
    if (resta > 0) {
      const segundos = Math.ceil(resta / 1000);
      relogio.textContent = "Faltam " + segundos + " s para consultar a expiração.";
      return;
    }
    clearInterval(estado.prazo);
    estado.prazo = null;
    const id = idAtual();
    if (!id) {
      relogio.textContent = "Os 2 minutos passaram. Informe o id para consultar as notificações.";
      return;
    }
    const resposta = await api("GET", "/api/notificacoes?ocorrenciaId=" + encodeURIComponent(id));
    mostrar(document.querySelector("#saida-notificacoes"), resposta);
    const lista = Array.isArray(resposta.corpo) ? resposta.corpo : [];
    const expiracao = lista.some((item) => item.tipo === "EXPIRACAO");
    relogio.textContent = expiracao
      ? "Há notificação EXPIRACAO nesta ocorrência."
      : "Nenhuma EXPIRACAO nesta ocorrência. Confirme no MySQL se o aceite deixou confirmado verdadeiro.";
  }, 1000);
});

document.querySelector("#btn-criar-extra").addEventListener("click", async () => {
  mostrar(document.querySelector("#saida-extra"), await api("POST", "/api/leitos/demonstracao/leito-extra", {
    tipoLeito: "UTI",
    codigo: "L-UTI-03",
    unidadeNome: "Hospital Central"
  }));
});

document.querySelector("#btn-apagar-extra").addEventListener("click", async () => {
  mostrar(document.querySelector("#saida-extra"), await api("DELETE", "/api/leitos/demonstracao/leito-extra/L-UTI-03"));
});

document.querySelector("#btn-trava").addEventListener("click", async () => {
  mostrar(document.querySelector("#saida-trava"), await api("POST", "/api/leitos/demonstracao/proxima-busca-sem-vaga", {
    tipoLeito: "UTI"
  }));
});

document.querySelector("#btn-circuito").addEventListener("click", async () => {
  mostrar(document.querySelector("#saida-circuito"), await api("GET", "/api/leitos/disponiveis?tipoLeito=UTI"));
});

document.querySelector("#btn-abertura-circuito").addEventListener("click", async () => {
  const resposta = await api("POST", "/api/ocorrencias", {
    paciente: { nome: "Teste Circuito" },
    local: "Rua do Circuito, 1",
    prioridade: "VERDE",
    tipoLeito: "ENFERMARIA"
  });
  if (resposta.corpo && resposta.corpo.id) {
    ocorrenciaId.value = resposta.corpo.id;
  }
  mostrar(document.querySelector("#saida-circuito"), resposta);
});

atualizarSessao();
if (estado.token) {
  consultarModo();
}
