package com.example.roadguard;

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
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
}
