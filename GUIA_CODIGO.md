# Guia para perceber o código do RoadGuard

Este guia acompanha o código atual. Começa pelas regras da aplicação; depois segue os métodos que as executam.

## 1. O que faz cada ficheiro

| Ficheiro Java | Responsabilidade |
| --- | --- |
| `MainActivity` | Lê o email e a palavra-passe, verifica a conta e guarda a sessão local. |
| `RegistoActivity` | Recolhe nome, email e palavra-passe e pede a criação da conta. |
| `MapActivity` | Liga os botões, o mapa, o GPS, as pesquisas, as viagens e os alertas. |
| `ZonasFavoritasActivity` | Mostra, cria e elimina zonas favoritas da conta. |
| `FirebaseServico` | Define os endereços da Firebase e os verbos GET, POST e DELETE. |
| `ClienteRetrofit` | Configura o Retrofit e o Gson e verifica a resposta HTTP. |
| `AlertasApi` | Faz os pedidos dos alertas e lê o nome do autor. |
| `ZonasFavoritasApi` | Faz os pedidos das zonas favoritas. |
| `TomTomApi` | Pesquisa destinos e obtém os pontos e instruções de uma rota. |
| `AlertaDTO` | Guarda os dados de um alerta, como latitude, categoria e autor. |
| `UtilizadorDTO` | Guarda os dados usados para verificar o login. |
| `ZonaFavorita` | Guarda o nome, o centro e o raio de uma zona. |
| `CategoriaAlerta` | Associa cada categoria ao seu nome, ícone e gravidade. |
| `VerificadorDeAlertas` | Decide se um alerta fica visível e calcula a proximidade na rota. |
| `PainelAvisos` | Mostra a caixa do aviso e fecha-a após 12 segundos ou ao tocar no ×. |

Os XML de `res/layout` descrevem os ecrãs. Os textos estão em `res/values/strings.xml`. Os desenhos e fundos estão em `res/drawable`.

## 2. Exemplo completo: consultar alertas

Segue esta ordem nos ficheiros:

1. `MapActivity.carregarAlertas()` pede os alertas a `AlertasApi.listar()`.
2. `AlertasApi` coloca o trabalho na fila `tarefasDeRede` para não bloquear o ecrã.
3. `FirebaseServico.listarAlertas()` identifica o pedido `GET Alerta.json`.
4. `ClienteRetrofit.executar()` envia o pedido e verifica se a resposta HTTP foi bem-sucedida.
5. O Gson lê o JSON. `lerAlerta()` verifica cada registo e preenche um `AlertaDTO`.
6. `entregar()` usa um `Handler` para devolver o resultado à thread principal do Android.
7. A Activity recebe a lista em `aoConcluir()` e guarda-a em `alertasRecebidos`.
8. `atualizarAlertasNoMapa()` decide quais devem ter um pino visível.

Se a ligação falhar, a resposta segue por `aoFalhar()` e a Activity apresenta a mensagem correspondente.

### O que significa Map?

Um `Map` associa uma chave a um valor. Neste caso, a chave é o identificador da Firebase.

```java
AlertaDTO alerta = alertasRecebidos.get(id);
```

Isto obtém um alerta com esse id.

Na Activity existem duas coleções com funções diferentes:

- `alertasRecebidos`: id -> dados do alerta recebidos da Firebase.
- `marcadoresDeAlertas`: id -> pino que está atualmente desenhado no mapa.

Um alerta pode estar guardado na primeira coleção sem ter um pino, por estar longe demais. Quando o GPS muda, a aplicação volta a verificar os dados que já recebeu.

## 3. As regras dos alertas

O método `VerificadorDeAlertas.deveMostrarNoMapa()` responde a uma pergunta: **este alerta deve ter um pino?**

1. Se o alerta está inativo, responde `false`.
2. Se está até 2000 metros do utilizador, responde `true`.
3. Se está mais longe e há uma viagem iniciada, responde `false`.
4. Fora da viagem, verifica se está dentro de uma das zonas favoritas visíveis.
5. Se não cumprir nenhuma condição, responde `false`.

`MapActivity.atualizarAlertasNoMapa()` chama essa regra para cada alerta e acrescenta ou retira o respetivo marcador.

A caixa de aviso tem outra regra: só aparece durante uma viagem e quando o perigo está à frente, no percurso, até 500 metros. Estar visível no mapa não significa que tenha de abrir um aviso.

## 4. Como se calcula a distância na rota

`medirDistancia()` usa a fórmula de Haversine para obter a distância em linha reta entre duas coordenadas. É usada para os raios de 2 km e das zonas favoritas.

Para os avisos, a distância deve seguir o percurso. Uma curva pode tornar o caminho mais longo do que a linha reta.

`projetarNaRota()` encontra o segmento do percurso mais próximo de uma coordenada. A rota é uma lista de pontos; cada par de pontos consecutivos forma um segmento.

O resultado é um objeto `PosicaoNaRota` com dois valores:

- `metrosDesdeInicio`: posição ao longo do percurso.
- `distanciaLateral`: afastamento em relação à estrada desenhada.

Exemplo: se o utilizador está nos 1200 metros do percurso e o alerta nos 1650 metros, faltam 450 metros até ao alerta. Se o alerta está nos 1000 metros, já ficou para trás.

No cálculo de cada segmento, `fracao` indica onde fica o ponto mais próximo: 0 é o início, 1 é o fim e 0,5 é o meio. A fórmula limita esse valor ao intervalo de 0 a 1.

As tolerâncias de 75 metros para o GPS e de 40 metros para o alerta permitem pequenos afastamentos da linha. Esta comparação geométrica é aproximada: não identifica a estrada pelo nome nem distingue sempre estradas muito próximas.

## 5. Como funciona o pop-up

`MapActivity.verificarAvisosDeAlertas()` prepara os avisos e coloca os mais próximos primeiro, usando um ciclo `while`.

Cada `PainelAvisos.Aviso` guarda apenas:

- o alerta;
- a distância em metros.

`PainelAvisos.atualizar()` recebe a lista. Mantém o aviso atual se ainda for válido; caso contrário, fecha-o e procura o seguinte.

`idsJaMostrados` guarda os ids dos avisos já apresentados. Assim, um aviso fechado não reaparece a cada atualização do GPS. A lista é limpa ao iniciar outra viagem.

O `Handler` agenda `fecharAtual()` para 12 segundos depois. Não fica uma thread parada à espera durante esse tempo.

## 6. Retrofit, Gson e pedidos HTTP

No `FirebaseServico`, as anotações indicam o pedido:

```java
@GET("Alerta.json")
Call<Map<String, JsonElement>> listarAlertas();
```

- `@GET`: queremos consultar dados.
- `Alerta.json`: caminho pedido à Firebase.
- `Call`: representa um pedido HTTP que pode ser executado.
- `Map<String, JsonElement>`: a resposta contém ids associados aos registos JSON.

`@POST` cria um registo. `@DELETE` elimina um registo pelo id. `@Body` indica o objeto enviado no corpo do pedido e `@Path` preenche uma parte variável do endereço.

O Gson converte JSON em Java e Java em JSON. No login, preenche diretamente os `UtilizadorDTO`. Nos alertas, cada registo é validado antes de ser convertido em `AlertaDTO`, para um registo inválido não impedir a leitura dos restantes.

Ao criar um alerta, `dataCriacao` envia `{ ".sv": "timestamp" }`. A Firebase substitui esse valor pela hora do servidor.

## 7. Os nomes que parecem mais complicados

| Nome | Significado neste projeto |
| --- | --- |
| `DTO` | Objeto que transporta dados, com campos e getters/setters. |
| `List` / `ArrayList` | Lista de elementos, percorrida com `for`. |
| `Map` / `HashMap` | Coleção que permite procurar um valor pela sua chave. |
| `Resposta<T>` | Indica à API o que fazer no sucesso e na falha. `T` é o tipo do resultado. |
| `aoConcluir()` | Método chamado quando a operação termina com sucesso. |
| `aoFalhar()` | Método chamado quando a operação falha. |
| `ExecutorService` | Fila que executa os pedidos em segundo plano. |
| `Handler` | Agenda uma ação ou envia-a para a thread principal. |
| `Runnable` | Uma ação que pode ser executada depois. |
| `view -> metodo()` | Forma curta de dizer que, ao acontecer esse evento, deve chamar o método. |
| `fechado` | Evita entregar uma resposta a um ecrã que já foi destruído. |

`ClienteRetrofit.executar()` usa `execute()`, que espera pela resposta. Por isso é chamado nas tarefas de segundo plano das APIs ou na `Thread` do login/registo. A interface continua a responder enquanto o pedido decorre.

`onCreate`, `onResume`, `onPause`, `onDestroy` e `onMapReady` têm nomes exigidos pelo Android ou pela TomTom. Os métodos criados para as regras do RoadGuard usam nomes em português.

## 8. Ordem sugerida para estudar

1. `AlertaDTO`, `ZonaFavorita` e `CategoriaAlerta`: que dados existem.
2. `FirebaseServico`: que pedidos são feitos.
3. `ClienteRetrofit` e `AlertasApi.listar()`: como chegam os dados.
4. `MapActivity.carregarAlertas()` e `atualizarAlertasNoMapa()`: como aparecem os pinos.
5. `VerificadorDeAlertas.deveMostrarNoMapa()`: regras dos 2 km e das zonas.
6. `verificarAvisosDeAlertas()` e `PainelAvisos`: quando aparece a caixa.
7. O cálculo geométrico e as restantes funcionalidades de pesquisa e viagem.

Para explicar uma funcionalidade, segue sempre: **ação do utilizador -> método chamado -> dados usados -> resultado no ecrã**.
