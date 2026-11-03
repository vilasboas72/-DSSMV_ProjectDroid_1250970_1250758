package com.example.roadguard.UI;

import com.example.roadguard.R;
import com.example.roadguard.Service.GestorAutenticacao;
import com.example.roadguard.Service.SessaoUtilizador;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {
    private EditText editEmail;
    private EditText editPassword;
    private Button btnLogin;
    private Button btnIrRegisto;
    private GestorAutenticacao autenticacao;
    private SessaoUtilizador sessao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        autenticacao = new GestorAutenticacao();
        sessao = new SessaoUtilizador(this);

        // Deixa espaço para as barras do sistema e permite deslocar o formulário com o teclado aberto.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.login_scroll), (view, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets keyboard = insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard.bottom));
            return insets;
        });

        // Verifica se existe a chave "UID_LOGADO", o que significa que há uma sessão ativa.
        if (sessao.temSessao()) {
            // Vai buscar o email que ficou guardado.
            String emailGuardado = sessao.obterEmail();

            // Constrói um Pop-up a perguntar se o utilizador quer usar a sessão guardada.
            android.app.AlertDialog dialogSessao = new android.app.AlertDialog.Builder(MainActivity.this)
                    .setTitle(R.string.saved_session_title)
                    .setMessage(getString(R.string.saved_session_message, emailGuardado))
                    .setCancelable(false) // Obriga o utilizador a escolher uma opção.
                    .setPositiveButton(R.string.continue_session, new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface dialog, int which) {
                            // Cria um Intent (intenção de viagem) para ir direto para o Mapa.
                            Intent intent = new Intent(MainActivity.this, MapActivity.class);
                            startActivity(intent); // Arranca para o mapa.
                            finish(); // Destrói o ecrã de login para o utilizador não conseguir voltar para trás.
                        }
                    })
                    .setNegativeButton(R.string.use_another_account, new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface dialog, int which) {
                            // Se quiser usar outra conta, limpa a sessão guardada.
                            sessao.limpar();
                        }
                    })
                    .create();

            dialogSessao.show();
            // Centra a janela da mensagem, independentemente da posição definida pelo tema.
            Window janela = dialogSessao.getWindow();
            if (janela != null) {
                janela.setGravity(Gravity.CENTER);
                WindowManager.LayoutParams parametros = janela.getAttributes();
                parametros.x = 0;
                parametros.y = 0;
                janela.setAttributes(parametros);
            }
        }

        editEmail = findViewById(R.id.edit_email);
        editPassword = findViewById(R.id.edit_password);
        btnLogin = findViewById(R.id.btn_login);
        btnIrRegisto = findViewById(R.id.btn_ir_registo);

        // O botão de concluir do teclado executa a mesma ação que o botão Entrar.
        editPassword.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                btnLogin.performClick();
                return true;
            }
            return false;
        });

        // Ação para o botão de Registo: ao clicar, viaja para a RegistoActivity.
        btnIrRegisto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, RegistoActivity.class);
                startActivity(intent);
            }
        });

        // O ecrã lê os campos; o gestor consulta e verifica a conta.
        btnLogin.setOnClickListener(view -> {
            String email = editEmail.getText().toString();
            String password = editPassword.getText().toString();
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, R.string.fill_login_fields, Toast.LENGTH_SHORT).show();
                return;
            }
            autenticacao.entrar(email, password, new GestorAutenticacao.RespostaLogin() {
                @Override
                public void aoConcluir(GestorAutenticacao.ResultadoLogin resultado) {
                    if (isFinishing() || isDestroyed()) return;
                    if (resultado.estado == GestorAutenticacao.EstadoLogin.SUCESSO) {
                        sessao.guardar(resultado.id, email);
                        startActivity(new Intent(MainActivity.this, MapActivity.class));
                        finish();
                    } else if (resultado.estado == GestorAutenticacao.EstadoLogin.SEM_UTILIZADORES) {
                        Toast.makeText(MainActivity.this, R.string.database_empty, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(MainActivity.this, R.string.login_invalid, Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void aoFalhar() {
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(MainActivity.this, R.string.login_error, Toast.LENGTH_LONG).show();
                }
            });
        });
    }
}
