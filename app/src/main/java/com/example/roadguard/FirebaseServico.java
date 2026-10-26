package com.example.roadguard;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

/** Endereços e verbos HTTP definidos pela equipa para a API REST da Firebase. */
interface FirebaseServico {
    @GET("Utilizador.json")
    Call<Map<String, UtilizadorDTO>> listarUtilizadores();

    @POST("Utilizador.json")
    Call<JsonObject> criarUtilizador(@Body Map<String, String> utilizador);

    @GET("Utilizador/{id}/nome.json")
    Call<String> obterNome(@Path("id") String id);

    // JsonElement permite validar e ignorar um registo incompleto sem perder os restantes.
    @GET("Alerta.json")
    Call<Map<String, JsonElement>> listarAlertas();

    @POST("Alerta.json")
    Call<JsonObject> criarAlerta(@Body JsonObject alerta);

    @DELETE("Alerta/{id}.json")
    Call<Void> eliminarAlerta(@Path("id") String id);

    @GET("Zonas_Favoritas/{utilizador}.json")
    Call<Map<String, JsonElement>> listarZonas(@Path("utilizador") String utilizador);

    @POST("Zonas_Favoritas/{utilizador}.json")
    Call<JsonObject> criarZona(@Path("utilizador") String utilizador, @Body JsonObject zona);

    @DELETE("Zonas_Favoritas/{utilizador}/{zona}.json")
    Call<Void> eliminarZona(@Path("utilizador") String utilizador, @Path("zona") String zona);
}
