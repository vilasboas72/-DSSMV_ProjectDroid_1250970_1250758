package com.example.roadguard;

import android.os.Bundle;
import android.util.Log;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registo);

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

        // Botão para criar a conta na Firebase via HTTP POST
        btnConfirmarRegisto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String nome = editNome.getText().toString().trim();
                final String email = editEmail.getText().toString().trim();
                final String password = editPassword.getText().toString().trim();

                if (!nome.isEmpty() && !email.isEmpty() && !password.isEmpty()) {

                    // Worker Thread para enviar o pedido POST
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                java.util.Map<String, String> novoUtilizador = new java.util.HashMap<>();
                                novoUtilizador.put("nome", nome);
                                novoUtilizador.put("email", email);
                                novoUtilizador.put("password", password);
                                // Gson transforma o Map em JSON no corpo do POST.
                                retrofit2.Response<com.google.gson.JsonObject> resposta =
                                        ClienteRetrofit.firebase.criarUtilizador(novoUtilizador).execute();
                                final int responseCode = resposta.code();
                                if (resposta.errorBody() != null) resposta.errorBody().close();
                                if (resposta.isSuccessful()) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(RegistoActivity.this, R.string.account_created, Toast.LENGTH_SHORT).show();
                                            finish(); // Fecha o ecrã de registo e volta ao Login
                                        }
                                    });
                                } else {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(RegistoActivity.this, getString(R.string.register_error_code, responseCode), Toast.LENGTH_LONG).show();
                                        }
                                    });
                                }
                            } catch (Exception e) {
                                Log.e("RegistoActivity", "Falha ao criar a conta", e);
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(RegistoActivity.this, R.string.register_error, Toast.LENGTH_LONG).show();
                                    }
                                });

                            }
                        }
                    }).start();

                } else {
                    Toast.makeText(RegistoActivity.this, R.string.fill_all_fields, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}
