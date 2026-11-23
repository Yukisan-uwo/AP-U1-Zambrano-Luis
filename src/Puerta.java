import java.io.IOException;
import java.util.Locale;
import java.util.Scanner;
import java.util.concurrent.*;

public class Puerta {
    public static void main(String[] args) throws IOException {
        if (args.length < 1 || args.length > 2 || !args[0].matches("[1-3]")) {
            System.err.println("Uso: Puerta <1|2|3> [token]");
            return;
        }
        int puerta = Integer.parseInt(args[0]);
        Configuracion config = Configuracion.leer();
        String token = args.length == 2 ? args[1] : config.token();
        Bitacora bitacora = new Bitacora(puerta);
        try (var latidos = Executors.newSingleThreadScheduledExecutor(); var consola = new Scanner(System.in)) {
            latidos.scheduleAtFixedRate(() -> solicitar(config, "LATIDO", puerta, token, bitacora),
                0, 2, TimeUnit.SECONDS);
            System.out.println("P" + puerta + " | entrar, salir, estado, fin");
            while (consola.hasNextLine()) {
                String orden = consola.nextLine().strip().toUpperCase(Locale.ROOT);
                switch (orden) {
                    case "FIN" -> { return; }
                    case "ENTRAR", "SALIR", "ESTADO" -> solicitar(config, orden, puerta, token, bitacora);
                    default -> System.out.println("Orden desconocida");
                }
            }
        }
    }

    private static void solicitar(Configuracion config, String orden, int puerta, String token, Bitacora bitacora) {
        try {
            Respuesta respuesta = Protocolo.solicitar(config, orden, puerta, token, bitacora);
            if (!orden.equals("LATIDO")) {
                System.out.printf("%s %s | dentro=%d/%d | %s%n", respuesta.resultado(), respuesta.motivo(),
                    respuesta.dentro(), respuesta.aforo(), respuesta.puertas());
            }
        } catch (IOException error) {
            if (!orden.equals("LATIDO")) {
                System.out.println("Coordinador inaccesible. Operación sin confirmar; no autorice el paso.");
            }
        }
    }
}
