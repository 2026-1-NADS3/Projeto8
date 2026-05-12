package com.example.maya_project;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

/**
 * Helper para enviar notificações locais do app.
 *
 * No Android 8+ é obrigatório criar um "canal de notificação" antes
 * de mandar a primeira notificação. Aqui isolamos isso num só lugar.
 *
 * No Android 13+ também é preciso pedir permissão de POST_NOTIFICATIONS
 * em tempo de execução (não basta declarar no manifest).
 */
public class NotificacaoHelper {

    private static final String ID_CANAL = "canal_maya";
    private static final String NOME_CANAL = "Lembretes da Clínica Maya";
    private static final String DESCRICAO_CANAL =
            "Lembretes de consultas, exercícios e check-ins";

    private static final int CODIGO_PERMISSAO = 1001;

    /**
     * Cria o canal de notificação (precisa ser feito uma vez antes de
     * mandar qualquer notificação no Android 8+).
     */
    public static void criarCanal(Context contexto) {
        // canais só existem a partir do Android 8 (API 26)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel canal = new NotificationChannel(
                    ID_CANAL,
                    NOME_CANAL,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            canal.setDescription(DESCRICAO_CANAL);

            NotificationManager manager =
                    contexto.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(canal);
            }
        }
    }

    /**
     * Pede permissão de notificação ao usuário (necessário no Android 13+).
     * Deve ser chamado de uma Activity.
     */
    public static void pedirPermissaoSeNecessario(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(activity,
                    Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        CODIGO_PERMISSAO);
            }
        }
    }

    /**
     * Envia uma notificação simples (título + texto).
     * Usa o ic_launcher como ícone pequeno (barra de status) e a logo
     * da Maya como ícone grande (dentro do card).
     */
    public static void enviar(Context contexto, int id, String titulo, String texto) {
        criarCanal(contexto);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(contexto, ID_CANAL)
                .setSmallIcon(R.drawable.mayaimagemlogo1) // logo Maya na barra de status
                .setLargeIcon(android.graphics.BitmapFactory.decodeResource(
                        contexto.getResources(),
                        R.drawable.maya)) // foto da Maya dentro do card
                .setContentTitle(titulo)
                .setContentText(texto)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(texto))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        NotificationManagerCompat manager = NotificationManagerCompat.from(contexto);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(contexto,
                    Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        manager.notify(id, builder.build());
    }
}