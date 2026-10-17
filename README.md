# 💰 CDG — Sistema de Controle de Gastos

![Java](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=java)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4-green?style=for-the-badge&logo=spring)
![MySQL](https://img.shields.io/badge/MySQL-005C84?style=for-the-badge&logo=mysql&logoColor=white)
![React](https://img.shields.io/badge/React-TypeScript-blue?style=for-the-badge&logo=react)

Sistema web **Fullstack** desenvolvido para facilitar o controle financeiro pessoal, permitindo gerenciar receitas e despesas, organizar categorias e visualizar o saldo em tempo real através de um dashboard intuitivo.

---

## 🧭 Visão Geral

O **CDG (Controle de Gastos)** é uma aplicação focada na organização financeira pessoal. Diferente de planilhas complexas, ele oferece uma interface limpa onde cada usuário possui seu próprio ambiente isolado (**Multi-Tenant**) para lançar seus gastos.

O sistema garante a segurança dos dados através de autenticação via Token e oferece feedback visual imediato sobre a saúde financeira do usuário.

---

## 🚀 Funcionalidades Principais

- 🔒 **Autenticação Segura:** Login e Cadastro de usuários com JWT (JSON Web Token).
- 💸 **Gestão de Transações:** Lançamento de Receitas e Despesas com data e descrição.
- 🏷️ **Categorias Personalizadas:** O usuário cria suas próprias categorias (ex: Alimentação, Lazer, Salário).
- 📊 **Dashboard Dinâmico:** Cálculo automático de saldo, total de entradas e saídas.
- 🤖 **Registro pelo Telegram:** Mensagens de texto são interpretadas, confirmadas no chat e aparecem no dashboard.
- 📄 **Extrato Detalhado:** Histórico visual de lançamentos com indicadores de cor (Verde/Vermelho).
- 🗑️ **Controle Total:** Possibilidade de excluir lançamentos errados (Lixeira).
- 🛡️ **Segurança de Dados:** Senhas criptografadas (BCrypt) e dados isolados por usuário.

---

## 🛠️ Tecnologias Utilizadas

### 🌐 Frontend
- **React + TypeScript + Vite** - Interface mobile para navegador, com consumo da API via `fetch`.
- **CSS próprio + Lucide** - Layout baseado no design B.

### ⚙️ Backend
- **Java 21**
- **Spring Boot 4**
- **Spring Security** (Autenticação e Autorização)
- **Spring Data JPA** (Persistência de dados)
- **MapStruct** (Mapeamento inteligente de DTOs)
- **Maven** (Gerenciamento de dependências)

### 🗄️ Banco de Dados
- **MySQL** - Banco de dados relacional.

---

## 🧩 Arquitetura

O projeto segue o padrão em camadas (**Layered Architecture**) para garantir desacoplamento e fácil manutenção:

1. **Controller:** Exposição dos endpoints REST (`/auth`, `/transacoes`, `/categorias`).
2. **Service:** Regras de negócio, validações e cálculos.
3. **Repository:** Camada de acesso a dados (JPA/Hibernate).
4. **Security:** Filtros de segurança (CORS e JWT).

---

## 💾 Instalação e Execução

### Pré-requisitos
- Java 21 instalado.
- MySQL rodando (local ou Docker).
- Maven.
- Node.js e npm.

### 1. Execute a API
Na raiz, execute `docker compose up --build -d` para iniciar API e banco. O frontend continua separado: use `cd frontend && npm install && npm run dev`. Para rodar a API fora do Docker, defina `DB_PASSWORD` e `JWT_SECRET` e execute `bash mvnw spring-boot:run`.

### 2. Execute o frontend
```bash
cd frontend
npm install
npm run dev
```
Abra `http://localhost:5173`. Durante o desenvolvimento, o Vite encaminha `/api` para `http://localhost:8080`. Para outra porta local, use `CDG_API_PROXY` conforme o [README do frontend](frontend/README.md). O login recebe `token` e `nome`; o token expira após 24 horas e a sessão do navegador é guardada por aba. A API devolve valores monetários como texto decimal para preservar os centavos no navegador.

### 3. Conecte o Telegram
Configure `TELEGRAM_BOT_TOKEN`, `TELEGRAM_WEBHOOK_SECRET`, `TELEGRAM_BOT_USERNAME` (nome do bot, sem `@`) e `GEMINI_API_KEY` no ambiente da API. `CDG_WEB_URL` deve apontar para a URL HTTPS pública do frontend, sem barra final. Cadastre no Telegram o webhook público `POST /integracoes/telegram/webhook` usando o mesmo segredo em `secret_token`.

Entre no navegador, toque no botão **Telegram** no topo da Visão geral e depois em **Conectar Telegram**. Com `TELEGRAM_BOT_USERNAME` configurado, o botão **Abrir no Telegram** leva direto ao bot com o código preenchido; basta tocar em **Iniciar**. Sem ele, envie o comando `/start <código>` mostrado na tela em uma conversa privada com o bot. A tela confirma a conexão sozinha. O código vale por 10 minutos. Se o bot já tiver criado uma conta sem e-mail, os lançamentos dela passam para a conta vinculada. Se estiver ligado a outro e-mail, os dados antigos permanecem naquela conta e os novos lançamentos passam a entrar na conta recém-vinculada.

Depois, envie uma mensagem como `gastei R$ 32,50 no almoço`, confira os dados que o bot devolveu e toque em **Confirmar**. O lançamento aparecerá no dashboard e no extrato ao recarregar a página. **Cancelar** não registra a transação. O botão **Abrir dashboard** enviado pelo bot abre o mesmo frontend como Mini App e valida a identidade assinada pelo Telegram.

Em bancos criados antes da mudança para `BigDecimal`, execute [scripts/migrate-telegram-money.sql](scripts/migrate-telegram-money.sql) uma vez para converter o valor das confirmações pendentes em `DECIMAL(19,2)`. Em um banco novo, a tabela já nasce com esse tipo.

## 💾 Estrutura de Dados

### 👤 Usuário (User)
Representa a conta de acesso ao sistema.

| Campo | Tipo | Descrição |
| :--- | :--- | :--- |
| `id` | `Long` | Identificador único (Auto Incremento) |
| `nome` | `String` | Nome completo do usuário |
| `email` | `String` | E-mail de login (Chave Única) |
| `senha` | `String` | Senha criptografada (BCrypt) |
| `categorias` | `List` | Lista de categorias criadas pelo usuário |
| `transacoes` | `List` | Histórico de transações do usuário |

### 🏷️ Categoria
Classificação para organizar as receitas e despesas (ex: Alimentação, Salário).

| Campo | Tipo | Descrição |
| :--- | :--- | :--- |
| `id` | `Long` | Identificador único |
| `nome` | `String` | Nome da categoria (ex: "Lazer") |
| `tipo` | `Enum` | Define se é `RECEITA` ou `DESPESA` |
| `usuario` | `User` | Relacionamento ManyToOne (Dono da categoria) |

### 💸 Transação
O registro financeiro propriamente dito (entrada ou saída de dinheiro).

| Campo | Tipo | Descrição |
| :--- | :--- | :--- |
| `id` | `Long` | Identificador único |
| `valor` | `BigDecimal` | Valor monetário com duas casas decimais (`DECIMAL(19,2)` no MySQL) |
| `descricao` | `String` | Detalhe do gasto (ex: "Pizza de Sexta") |
| `data` | `LocalDate` | Data em que ocorreu o lançamento |
| `tipo` | `Enum` | Herda o tipo da categoria (Receita/Despesa) |
| `categoria` | `Categoria` | Relacionamento ManyToOne (Vínculo) |
| `usuario` | `User` | Relacionamento ManyToOne (Dono do dado) |

## 🔌 Endpoints da API

Documentação das rotas disponíveis na API REST.

| Método | Rota | Descrição |
|:---:|:---|:---|
| `POST` | `/auth/register` | Criação de novo usuário |
| `POST` | `/auth/login` | Autenticação (retorna token JWT e nome) |
| `POST` | `/auth/telegram/webapp` | Autentica a Mini App com dados assinados pelo Telegram |
| `GET/POST` | `/integracoes/telegram/vinculo` | Consulta o vínculo ou gera o código de conexão (com link `t.me` quando `TELEGRAM_BOT_USERNAME` está definido) |
| `POST` | `/integracoes/telegram/webhook` | Recebe mensagens e confirmações do bot |
| `GET` | `/transacoes/dashboard` | Retorna o resumo financeiro (Saldo, Receitas, Despesas) |
| `GET` | `/transacoes/resumo?mes=AAAA-MM` | Resumo do mês: totais, comparação com o mês anterior, saldo acumulado e total por categoria |
| `POST` | `/transacoes` | Cria nova receita ou despesa |
| `DELETE` | `/transacoes/{id}` | Remove um lançamento |
| `GET` | `/categorias` | Lista as categorias cadastradas pelo usuário |

---

## 🧠 Autor

**Hanrry** Desenvolvedor Backend Java em formação | Foco em Spring Boot e Arquitetura de Software

[![LinkedIn](https://img.shields.io/badge/LinkedIn-0077B5?style=for-the-badge&logo=linkedin&logoColor=white)](https://www.linkedin.com/in/hanrrysantos)
[![GitHub](https://img.shields.io/badge/GitHub-100000?style=for-the-badge&logo=github&logoColor=white)](https://github.com/hanrrysantos)

---

## 📜 Licença

Este projeto é de uso livre para fins de estudo e portfólio.
