import java.util.ArrayList;
import java.util.concurrent.*;

public class PruebaCarga {
    public static void main(String[] args) throws Exception {
        var config = Configuracion.leer();
        var bitacoras = new Bitacora[] {new Bitacora(1), new Bitacora(2), new Bitacora(3)};
        var listas = new CountDownLatch(30);
        var salida = new CountDownLatch(1);
        var respuestas = new ArrayList<Future<Respuesta>>();
        try (var hilos = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 30; i++) {
                int puerta = i / 10 + 1;
                respuestas.add(hilos.submit(() -> {
                    listas.countDown();
                    salida.await();
                    return Protocolo.solicitar(config, "ENTRAR", puerta, config.token(), bitacoras[puerta - 1]);
                }));
            }
            listas.await();
            salida.countDown();
            int permitidas = 0;
            int rechazadas = 0;
            int errores = 0;
            for (var tarea : respuestas) {
                try {
                    if (tarea.get().resultado().equals("PERMITIDA")) permitidas++;
                    else rechazadas++;
                } catch (ExecutionException error) {
                    errores++;
                    System.err.println(error.getCause());
                }
            }
            var estado = Protocolo.solicitar(config, "ESTADO", 1, config.token(), bitacoras[0]);
            System.out.printf("Ejecutadas=30 Permitidas=%d Rechazadas=%d Errores=%d Dentro=%d Aforo=%d%n",
                permitidas, rechazadas, errores, estado.dentro(), estado.aforo());
            if (errores > 0) System.exit(1);
        }
    }
}
