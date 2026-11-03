package com.example.roadguard.Service;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.example.roadguard.DTO.UtilizadorDTO;
import com.example.roadguard.API.ClienteRetrofit;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import retrofit2.Response;

/** Coordena os pedidos de login e registo sem depender dos campos ou botões do ecrã. */
public final class GestorAutenticacao {
    public enum EstadoLogin { SUCESSO, SEM_UTILIZADORES, DADOS_INVALIDOS }

    public static final class ResultadoLogin {
        public final EstadoLogin estado;
        public final String id;

        ResultadoLogin(EstadoLogin estado, String id) {
            this.estado = estado;
            this.id = id;
        }
    }

    public interface RespostaLogin {
        void aoConcluir(ResultadoLogin resultado);
        void aoFalhar();
    }

    public interface RespostaRegisto {
        void aoConcluir();
        // Zero representa uma falha de ligação; os restantes valores são códigos HTTP.
        void aoFalhar(int codigoHttp);
    }

    private final Handler principal = new Handler(Looper.getMainLooper());

    // O GET corre em segundo plano; o resultado é entregue na thread do ecrã.
    public void entrar(String email, String password, RespostaLogin resposta) {
        new Thread(() -> {
            try {
                Map<String, UtilizadorDTO> utilizadores = ClienteRetrofit.executar(
                        ClienteRetrofit.firebase.listarUtilizadores());
                ResultadoLogin resultado = verificarCredenciais(utilizadores, email, password);
                principal.post(() -> resposta.aoConcluir(resultado));
            } catch (Exception erro) {
                Log.e("GestorAutenticacao", "Falha ao consultar as contas", erro);
                principal.post(resposta::aoFalhar);
            }
        }).start();
    }

    // Compara uma conta de cada vez e devolve o id da conta encontrada.
    public static ResultadoLogin verificarCredenciais(Map<String, UtilizadorDTO> utilizadores,
                                                       String email, String password) {
        if (utilizadores == null || utilizadores.isEmpty()) {
            return new ResultadoLogin(EstadoLogin.SEM_UTILIZADORES, "");
        }
        for (Map.Entry<String, UtilizadorDTO> conta : utilizadores.entrySet()) {
            UtilizadorDTO utilizador = conta.getValue();
            if (utilizador == null) continue;
            if (email.equals(utilizador.getEmail()) && password.equals(utilizador.getPassword())) {
                return new ResultadoLogin(EstadoLogin.SUCESSO, conta.getKey());
            }
        }
        return new ResultadoLogin(EstadoLogin.DADOS_INVALIDOS, "");
    }

    // O Gson converte os campos em JSON para o POST já existente na Firebase.
    public void registar(String nome, String email, String password, RespostaRegisto resposta) {
        new Thread(() -> {
            try {
                Map<String, String> utilizador = new HashMap<>();
                utilizador.put("nome", nome);
                utilizador.put("email", email);
                utilizador.put("password", password);
                Response<JsonObject> resultado = ClienteRetrofit.firebase.criarUtilizador(utilizador).execute();
                int codigo = resultado.code();
                if (resultado.errorBody() != null) resultado.errorBody().close();
                if (resultado.isSuccessful()) {
                    principal.post(resposta::aoConcluir);
                } else {
                    principal.post(() -> resposta.aoFalhar(codigo));
                }
            } catch (Exception erro) {
                Log.e("GestorAutenticacao", "Falha ao criar a conta", erro);
                principal.post(() -> resposta.aoFalhar(0));
            }
        }).start();
    }
}
