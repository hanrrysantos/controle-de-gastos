# 💰 CDG — Sistema de Controle de Gastos

![Java](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=java)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3-green?style=for-the-badge&logo=spring)
![MySQL](https://img.shields.io/badge/MySQL-005C84?style=for-the-badge&logo=mysql&logoColor=white)
![Bootstrap](https://img.shields.io/badge/Bootstrap-5-purple?style=for-the-badge&logo=bootstrap)

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
- 📄 **Extrato Detalhado:** Histórico visual de lançamentos com indicadores de cor (Verde/Vermelho).
- 🗑️ **Controle Total:** Possibilidade de excluir lançamentos errados (Lixeira).
- 🛡️ **Segurança de Dados:** Senhas criptografadas (BCrypt) e dados isolados por usuário.

---

## 🛠️ Tecnologias Utilizadas

### 🌐 Frontend
- **HTML5 & CSS3**
- **JavaScript (ES6+)** - Consumo de API via `fetch`.
- **Bootstrap 5** - Design responsivo e componentes (Modais, Cards, Tabelas).

### ⚙️ Backend
- **Java 21**
- **Spring Boot 3**
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

### 1. Clone o repositório
```bash
git clone [https://github.com/seu-usuario/controle-de-gastos.git](https://github.com/seu-usuario/controle-de-gastos.git)
cd controle-de-gastos
```

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
| `valor` | `Double` | Valor monetário da transação |
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
| `POST` | `/auth/login` | Autenticação (Retorna Token JWT) |
| `GET` | `/transacoes/dashboard` | Retorna o resumo financeiro (Saldo, Receitas, Despesas) |
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
