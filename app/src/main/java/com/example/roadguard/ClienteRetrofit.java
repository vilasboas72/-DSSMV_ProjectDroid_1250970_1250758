package com.example.roadguard;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.HttpException;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Url;

/** Configuração partilhada dos pedidos. Não faz pedidos na thread do ecrã. */
final class ClienteRetrofit {
    // Gson faz as conversões JSON -> Java e Java -> JSON.
    static final Gson gson = new Gson();
    private static final OkHttpClient clienteHttp = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            // Um POST não deve ser repetido automaticamente após uma falha de ligação.
            .retryOnConnectionFailure(false)
            .build();
    static final FirebaseServico firebase = criar(
            "https://roadguard-25b31-default-rtdb.europe-west1.firebasedatabase.app/")
            .create(FirebaseServico.class);

    // @Url aceita os endereços de pesquisa e de rota preparados na TomTomApi.
    interface TomTomServico {
        @GET
        Call<JsonObject> obter(@Url String endereco);
    }
    static final TomTomServico tomTom = criar("https://api.tomtom.com/")
            .create(TomTomServico.class);

    static Retrofit criar(String endereco) {
        return new Retrofit.Builder().baseUrl(endereco).client(clienteHttp)
                .addConverterFactory(GsonConverterFactory.create(gson)).build();
    }

    // As APIs já chamam este método nas suas tarefas de segundo plano.
    // <T> permite usar o mesmo método com os diferentes tipos de resposta.
    static <T> T executar(Call<T> pedido) throws IOException {
        Response<T> resposta = pedido.execute();
        if (!resposta.isSuccessful()) {
            if (resposta.errorBody() != null) resposta.errorBody().close();
            throw new HttpException(resposta);
        }
        return resposta.body();
    }
}
