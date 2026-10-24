package com.example.roadguard;

/** Classificação inicial por categoria; a gravidade é atribuída sem perguntas adicionais. */
enum CategoriaAlerta {
    OBRAS("obras", R.string.alert_works, R.drawable.ic_alert_works, "media"),
    ESTRADA_INTERDITA("estrada_interdita", R.string.alert_road_closed, R.drawable.ic_alert_road_closed, "elevada"),
    POLICIA("policia", R.string.alert_police, R.drawable.ic_alert_police, "baixa"),
    ACIDENTE("acidente", R.string.alert_accident, R.drawable.ic_alert_accident, "elevada"),
    BURACO("buraco", R.string.alert_pothole, R.drawable.ic_alert_pothole, "media"),
    TRANSITO("transito_congestionado", R.string.alert_traffic, R.drawable.ic_alert_traffic, "baixa");

    final String id;
    final int nome;
    final int icone;
    final String gravidade;

    CategoriaAlerta(String id, int nome, int icone, String gravidade) {
        this.id = id;
        this.nome = nome;
        this.icone = icone;
        this.gravidade = gravidade;
    }

    static CategoriaAlerta procurar(String id) {
        for (CategoriaAlerta categoria : values()) {
            if (categoria.id.equals(id)) return categoria;
        }
        return null;
    }
}
