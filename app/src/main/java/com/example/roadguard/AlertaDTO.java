package com.example.roadguard;

import java.util.HashMap;
import java.util.Map;

/** Dados de um perigo guardado no nó Alerta. O identificador é a chave da Firebase. */
public class AlertaDTO {
    private String id;
    private String utilizadorId;
    private String categoriaId;
    private String gravidade;
    private double latitude;
    private double longitude;
    private long dataCriacao;
    private boolean ativo;
    // Uma entrada por conta: true = ainda existe; false = já não existe.
    private Map<String, Boolean> confirmacoes = new HashMap<>();

    public AlertaDTO() { }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUtilizadorId() { return utilizadorId; }
    public void setUtilizadorId(String utilizadorId) { this.utilizadorId = utilizadorId; }
    public String getCategoriaId() { return categoriaId; }
    public void setCategoriaId(String categoriaId) { this.categoriaId = categoriaId; }
    public String getGravidade() { return gravidade; }
    public void setGravidade(String gravidade) { this.gravidade = gravidade; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public long getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(long dataCriacao) { this.dataCriacao = dataCriacao; }
    public boolean isAtivo() { return ativo && !foiResolvido(); }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    public boolean jaConfirmou(String utilizador) {
        return confirmacoes != null && confirmacoes.containsKey(utilizador);
    }

    public void guardarConfirmacao(String utilizador, boolean aindaExiste) {
        if (confirmacoes == null) confirmacoes = new HashMap<>();
        confirmacoes.put(utilizador, aindaExiste);
    }

    public boolean foiResolvido() {
        int existe = 0;
        int naoExiste = 0;
        if (confirmacoes == null) return false;
        for (Map.Entry<String, Boolean> voto : confirmacoes.entrySet()) {
            // O autor não participa na confirmação do próprio alerta.
            if (voto.getKey().equals(utilizadorId) || voto.getKey().isEmpty()) continue;
            if (Boolean.TRUE.equals(voto.getValue())) existe++;
            else if (Boolean.FALSE.equals(voto.getValue())) naoExiste++;
        }
        return naoExiste >= 3 && naoExiste > existe;
    }
}
