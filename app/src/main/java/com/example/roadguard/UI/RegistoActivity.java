package com.example.roadguard.UI;

import com.example.roadguard.R;
import com.example.roadguard.Service.GestorAutenticacao;

import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class RegistoActivity extends AppCompatActivity {

    private EditText editNome;
    private EditText editEmail;
    private EditText editPassword;
    private Button btnConfirmarRegisto;
    private Button btnVoltarLogin;
    private GestorAutenticacao autenticacao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registo);
        autenticacao = new GestorAutenticacao();

        // Mantém os campos acessíveis com o teclado aberto e respeita as barras do sistema.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.registo_scroll), (view, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets keyboard = insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard.bottom));
            return insets;
        });

        editNome = findViewById(R.id.edit_registo_nome);
        editEmail = findViewById(R.id.edit_registo_email);
        editPassword = findViewById(R.id.edit_registo_password);
        btnConfirmarRegisto = findViewById(R.id.btn_confirmar_registo);
        btnVoltarLogin = findViewById(R.id.btn_voltar_login);

        editPassword.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                btnConfirmarRegisto.performClick();
                return true;
            }
            return false;
        });

        // Botão para voltar atrás caso o utilizador se engane
        btnVoltarLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // O gestor envia o POST; o ecrã apresenta o sucesso ou o erro.
        btnConfirmarRegisto.setOnClickListener(view -> {
            // AÇÃO DO UTILIZADOR — Confirmar registo
            String nome = editNome.getText().toString().trim();
            String email = editEmail.getText().toString().trim();
            String password = editPassword.getText().toString().trim();
            if (nome.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, R.string.fill_all_fields, Toast.LENGTH_SHORT).show();
                return;
            }
            autenticacao.registar(nome, email, password, new GestorAutenticacao.RespostaRegisto() {
                @Override
                public void aoConcluir() {
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(RegistoActivity.this, R.string.account_created, Toast.LENGTH_SHORT).show();
                    finish();
                }

                @Override
                public void aoFalhar(int codigoHttp) {
                    if (isFinishing() || isDestroyed()) return;
                    if (codigoHttp == 0) {
                        Toast.makeText(RegistoActivity.this, R.string.register_error, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(RegistoActivity.this,
                                getString(R.string.register_error_code, codigoHttp), Toast.LENGTH_LONG).show();
                    }
                }
            });
        });
    }
}
