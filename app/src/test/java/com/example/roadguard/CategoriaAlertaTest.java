package com.example.roadguard;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/** Testes simples para procurar as categorias de perigo. */
public class CategoriaAlertaTest {

    @Test
    public void encontraCategoriaPeloIdentificador() {
        assertSame(CategoriaAlerta.ACIDENTE,
                CategoriaAlerta.procurar("acidente"));
    }

    @Test
    public void devolveNuloQuandoCategoriaNaoExiste() {
        assertNull(CategoriaAlerta.procurar("categoria_inventada"));
    }

    @Test
    public void cadaCategoriaTemUmaGravidadeDefinida() {
        assertEquals("elevada", CategoriaAlerta.ACIDENTE.gravidade);
        assertEquals("media", CategoriaAlerta.BURACO.gravidade);
        assertEquals("baixa", CategoriaAlerta.POLICIA.gravidade);
    }
}
