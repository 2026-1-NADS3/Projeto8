# Plano de Testes de Aceitação — App Clínica Maya

**Disciplina:** Testes e Qualidade de Software (DEVOPS)
**Projeto:** Aplicativo Maya Yoshiko Yamamoto RPG (Fisioterapia)
**Data:** 11/05/2026
**Versão do app:** Entrega 2

---

## 1. Objetivo

Verificar se o aplicativo atende aos requisitos funcionais do cliente
(Clínica Maya Yoshiko Yamamoto RPG), validando os fluxos principais do
ponto de vista do usuário final (fisioterapeuta e paciente).

## 2. Escopo dos testes

Foram selecionados **6 cenários de aceitação** cobrindo as funcionalidades
críticas implementadas nas Entregas 1 e 2 do projeto:

1. Login do administrador
2. Login do paciente via API REST
3. Visualização do plano de exercícios prescritos (vindo da API)
4. Registro de check-in com nível de dor (persistência SQLite)
5. Visualização do histórico de evolução com gráfico
6. Recebimento de notificação local após check-in

## 3. Ambiente de testes

| Item | Valor |
|---|---|
| Dispositivo | Emulador Pixel 8 Pro |
| Android | API 37 (Android 17) |
| Tipo de execução | Manual |
| API REST utilizada | mockapi.io (provisório) |
| Banco local | SQLite |

## 4. Pré-requisitos gerais

- App instalado no emulador
- Conexão com a internet ativa
- Dados pré-cadastrados na API mockapi.io
- Permissão de notificação concedida

---

## 5. Casos de Teste

### CT-01 — Login do Administrador

**Objetivo:** Verificar que o administrador consegue acessar a tela administrativa.

**Pré-condição:** App aberto na tela de login.

**Passos:**
1. Digitar e-mail: `admin@gmail.com`
2. Digitar senha: `admin123`
3. Tocar no botão "Entrar"

**Resultado esperado:**
- Aparece toast "Bem-vindo(a), Maya Yoshiko Yamamoto!"
- App navega para a tela de Administrador
- Botões "Agendar Consulta", "Aniversariantes" e "Sair" estão visíveis

**Status:** ✅ APROVADO

---

### CT-02 — Login do Paciente (via API REST)

**Objetivo:** Verificar que o paciente consegue se autenticar usando dados que
vêm da API REST (mockapi.io).

**Pré-condição:** Usuário `usuario@gmail.com` cadastrado no mockapi.io.

**Passos:**
1. Digitar e-mail: `usuario@gmail.com`
2. Digitar senha: `usuario123`
3. Tocar no botão "Entrar"

**Resultado esperado:**
- App faz requisição GET para `/usuarios` na API
- Resposta da API é tratada e validada
- Aparece toast com nome do paciente
- App navega para a tela do Paciente

**Status:** ✅ APROVADO

---

### CT-03 — Visualização do Plano de Exercícios (vindo da API)

**Objetivo:** Verificar que o paciente consegue visualizar os exercícios
prescritos, com os dados sendo carregados da API REST.

**Pré-condição:** Paciente logado.

**Passos:**
1. Na tela do paciente, tocar em "Exercícios para Postura"
2. Aguardar o carregamento dos dados

**Resultado esperado:**
- App faz requisição GET para `/exercicios` na API
- 3 exercícios são exibidos em cards: nome, descrição, frequência
- Resumo mostra: total de exercícios, tempo total, concluídos hoje

**Status:** ✅ APROVADO

---

### CT-04 — Registro de Check-in com Nível de Dor

**Objetivo:** Verificar que o paciente consegue marcar um exercício como feito
e informar o nível de dor, com persistência no SQLite local.

**Pré-condição:** Tela de Exercícios aberta.

**Passos:**
1. Tocar em "Marcar como feito" em qualquer exercício
2. No diálogo, selecionar nível de dor (ex: "5 — Moderada")
3. Tocar em "Confirmar"

**Resultado esperado:**
- Diálogo de seleção de dor abre normalmente
- Após confirmar, aparece toast "Check-in registrado! Dor: 5/10"
- Botão muda para "✓ Concluído" e fica desabilitado
- Contador de concluídos no resumo é incrementado
- Dado é persistido no banco SQLite local

**Status:** ✅ APROVADO

---

### CT-05 — Visualização do Histórico com Gráfico

**Objetivo:** Verificar que o paciente consegue visualizar a evolução dos seus
check-ins através de indicadores e gráfico de barras.

**Pré-condição:** Pelo menos 1 check-in realizado.

**Passos:**
1. Voltar para a tela do paciente
2. Tocar em "Meu Histórico"

**Resultado esperado:**
- Tela de histórico abre
- Cards mostram: total de check-ins, check-ins desta semana
- Gráfico de barras exibe os últimos check-ins com cores variando conforme intensidade da dor
- Card de "Nível médio de dor" mostra a média calculada
- Lista detalhada exibe cada check-in com data, exercício e nível de dor

**Status:** ✅ APROVADO

---

### CT-06 — Notificação Local após Check-in

**Objetivo:** Verificar que o sistema dispara uma notificação local após
o paciente registrar um check-in.

**Pré-condição:** Permissão de notificação concedida.

**Passos:**
1. Realizar um check-in (CT-04)
2. Arrastar a barra de status do dispositivo para baixo

**Resultado esperado:**
- Notificação aparece com ícone monocromático na barra de status
- Card da notificação mostra:
    - Título: "Check-in registrado! 💪"
    - Texto: "Você fez [nome do exercício]. Nível de dor: X/10. Continue assim!"
    - Foto da Maya como ícone grande
- Notificação some ao ser tocada

**Status:** ✅ APROVADO

---

## 6. Sumário dos Resultados

| Caso de Teste | Resultado | Observações |
|---|---|---|
| CT-01 — Login Admin | ✅ APROVADO | — |
| CT-02 — Login Paciente via API | ✅ APROVADO | API responde rapidamente |
| CT-03 — Exercícios via API | ✅ APROVADO | Dados carregam em ~500ms |
| CT-04 — Check-in com Dor | ✅ APROVADO | SQLite persistindo OK |
| CT-05 — Histórico com Gráfico | ✅ APROVADO | Indicadores corretos |
| CT-06 — Notificação Local | ✅ APROVADO | Ícone e textos OK |

**Taxa de aprovação: 6/6 (100%)**

## 7. Conclusão

Todos os casos de teste de aceitação foram aprovados. O aplicativo atende
aos requisitos funcionais especificados pelo cliente para a Entrega 2.
O sistema está apto para ser disponibilizado em ambiente de homologação.

**Recomendações para próximas iterações:**
- Substituir a API mockapi.io pelo backend definitivo (Node.js + MySQL)
- Implementar notificações push agendadas para lembretes de consulta
- Adicionar suporte a vídeos demonstrativos dos exercícios
- Implementar testes automatizados de UI com Espresso