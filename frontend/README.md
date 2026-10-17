# Frontend do Controle de Gastos

Interface mobile para navegador, feita com React, TypeScript e Vite.

```bash
npm install
npm run dev
```

Abra `http://localhost:5173`. A API Spring Boot deve estar em `http://localhost:8080`; o proxy do Vite encaminha as chamadas em `/api`. Para usar outra porta local, inicie com `CDG_API_PROXY=http://localhost:8081 npm run dev`. Para usar outra URL pública de API, defina `VITE_API_URL`.

Use `npm run build` para gerar a versão estática em `dist/`. O servidor que publicar essa pasta deve encaminhar as rotas da interface para `index.html` e as chamadas da API para o backend.
