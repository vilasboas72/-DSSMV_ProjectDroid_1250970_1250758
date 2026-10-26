package com.example.roadguard;

import com.google.gson.JsonObject;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.Dispatcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

/** Servidor local: os testes nunca consultam nem alteram a Firebase real. */
public class FirebaseServicoTest {
    private MockWebServer servidor;
    private FirebaseServico servico;
    private String metodo;
    private String caminho;
    private String corpo;
    private String resposta;
    private int estado;

    @Before public void preparar() throws Exception {
        resposta = "null";
        estado = 200;
        servidor = new MockWebServer();
        servidor.setDispatcher(new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest pedido) {
                metodo = pedido.getMethod();
                caminho = pedido.getPath();
                corpo = pedido.getBody().readUtf8();
                return new MockResponse().setResponseCode(estado)
                        .setHeader("Content-Type", "application/json").setBody(resposta);
            }
        });
        servidor.start();
        servico = ClienteRetrofit.criar(servidor.url("/").toString()).create(FirebaseServico.class);
    }
    @After public void fechar() throws Exception { servidor.shutdown(); }

    @Test public void getConverteUtilizadoresEmObjetosJava() throws Exception {
        resposta = "{\"uid1\":{\"email\":\"teste@exemplo.pt\",\"password\":\"exemplo\"}}";
        UtilizadorDTO utilizador = ClienteRetrofit.executar(servico.listarUtilizadores()).get("uid1");
        assertEquals("teste@exemplo.pt", utilizador.getEmail());
        assertEquals("GET", metodo);
        assertEquals("/Utilizador.json", caminho);
    }
    @Test public void getNomeLeApenasONomeDoAutor() throws Exception {
        resposta = "\"Gonçalo\"";
        assertEquals("Gonçalo", ClienteRetrofit.executar(servico.obterNome("uid1")));
        assertEquals("/Utilizador/uid1/nome.json", caminho);
    }
    @Test public void postEnviaJsonERecebeIdentificador() throws Exception {
        resposta = "{\"name\":\"novoId\"}";
        Map<String, String> utilizador = new HashMap<>();
        utilizador.put("nome", "Teste");
        assertEquals("novoId", ClienteRetrofit.executar(servico.criarUtilizador(utilizador))
                .get("name").getAsString());
        assertEquals("POST", metodo);
        assertEquals("Teste", ClienteRetrofit.gson.fromJson(corpo, JsonObject.class)
                .get("nome").getAsString());
    }
    @Test public void postDoAlertaPreservaTimestampDoServidor() throws Exception {
        resposta = "{\"name\":\"alerta1\"}";
        JsonObject alerta = new JsonObject();
        JsonObject data = new JsonObject();
        data.addProperty(".sv", "timestamp");
        alerta.add("dataCriacao", data);
        ClienteRetrofit.executar(servico.criarAlerta(alerta));
        assertEquals("POST", metodo);
        assertEquals("/Alerta.json", caminho);
        assertEquals("timestamp", ClienteRetrofit.gson.fromJson(corpo, JsonObject.class)
                .getAsJsonObject("dataCriacao").get(".sv").getAsString());
    }
    @Test public void deleteDaZonaUsaOsDoisIdentificadores() throws Exception {
        ClienteRetrofit.executar(servico.eliminarZona("uid1", "zona1"));
        assertEquals("DELETE", metodo);
        assertEquals("/Zonas_Favoritas/uid1/zona1.json", caminho);
    }
    @Test public void deleteDoAlertaMantemVerboHttp() throws Exception {
        ClienteRetrofit.executar(servico.eliminarAlerta("alerta1"));
        assertEquals("DELETE", metodo);
        assertEquals("/Alerta/alerta1.json", caminho);
    }
    @Test public void noVazioDaFirebaseDevolveNull() throws Exception {
        assertNull(ClienteRetrofit.executar(servico.listarAlertas()));
    }
    @Test public void erroDePermissaoNaoETratadoComoSucesso() throws Exception {
        estado = 403;
        try {
            ClienteRetrofit.executar(servico.listarAlertas());
            fail("Esperava erro HTTP");
        } catch (retrofit2.HttpException erro) { assertEquals(403, erro.code()); }
    }
}
