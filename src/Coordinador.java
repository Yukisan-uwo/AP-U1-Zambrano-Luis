import java.io.*;
import java.net.*;
import java.util.StringJoiner;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Coordinador {
    private final Configuracion config;
    private final boolean inseguro;
    private final Bitacora bitacora;
    private final AtomicInteger dentro = new AtomicInteger();
    private final long[] ultimoLatido = new long[4];
    private final boolean[] activas = new boolean[4];
    private final Object aforo = new Object();

    private Coordinador(Configuracion config, boolean inseguro) throws IOException {
        this.config = config;
        this.inseguro = inseguro;
        bitacora = new Bitacora(0);
    }

    public static void main(String[] args) throws IOException {
        new Coordinador(Configuracion.leer(), args.length > 0 && args[0].equals("--inseguro")).ejecutar();
    }

    private void ejecutar() throws IOException {
        try (var servidor = new ServerSocket();
             var clientes = Executors.newVirtualThreadPerTaskExecutor();
             var monitor = Executors.newSingleThreadScheduledExecutor()) {
            servidor.bind(new InetSocketAddress(config.host(), config.puerto()));
            monitor.scheduleAtFixedRate(this::vigilar, 0, 100, TimeUnit.MILLISECONDS);
            System.out.printf("Coordinador %s en puerto %d%n", inseguro ? "inseguro" : "normal", config.puerto());
            while (true) {
                Socket socket = servidor.accept();
                clientes.submit(() -> atender(socket));
            }
        }
    }

    private void atender(Socket socket) {
        try (socket; var entrada = Protocolo.lector(socket); var salida = Protocolo.escritor(socket)) {
            socket.setSoTimeout(3000);
            String linea;
            while ((linea = entrada.readLine()) != null) {
                Protocolo.enviar(salida, procesar(linea).linea());
            }
        } catch (IOException error) {
            System.err.println("Conexión terminada: " + error.getMessage());
        }
    }

    private Respuesta procesar(String linea) throws IOException {
        String[] campos = linea.split("\\|", -1);
        String detalle = "mensaje inválido";
        String motivo;
        try {
            if (campos.length != 5) throw new IllegalArgumentException();
            String operacion = campos[0];
            int puerta = Integer.parseInt(campos[1]);
            long marca = Long.parseLong(campos[3]);
            String solicitud = java.util.UUID.fromString(campos[4]).toString();
            if (marca < 0 || marca >= Long.MAX_VALUE - 2 || !operacion.matches("[A-Z_]+")) {
                throw new IllegalArgumentException();
            }
            detalle = operacion + " P" + puerta + " " + solicitud;
            bitacora.recibir(marca, detalle);
            if (campos[2].isEmpty() || !config.token().equals(campos[2])) {
                motivo = "token inválido";
            } else if (puerta < 1 || puerta > 3) {
                motivo = "puerta inválida";
            } else {
                motivo = switch (operacion) {
                    case "ESTADO" -> "";
                    case "LATIDO" -> registrarLatido(puerta);
                    case "ENTRAR", "SALIR" -> cambiarAforo(operacion.equals("ENTRAR"));
                    default -> "operación inválida";
                };
            }
        } catch (IllegalArgumentException error) {
            motivo = "mensaje inválido";
            bitacora.evento("RECHAZO", detalle);
        }
        String resultado = motivo.isEmpty() ? "PERMITIDA" : "RECHAZADA";
        if (!motivo.isEmpty()) bitacora.evento("RECHAZO", detalle + " " + motivo);
        int total = dentro.get();
        String puertas = estadoPuertas();
        long marca = bitacora.evento("ENVIAR", detalle + " " + resultado + " dentro=" + total);
        return new Respuesta(resultado, motivo, total, config.aforo(), puertas, marca);
    }

    private String cambiarAforo(boolean entrada) {
        if (inseguro) return comprobarYActualizar(entrada);
        synchronized (aforo) {
            return comprobarYActualizar(entrada);
        }
    }

    private String comprobarYActualizar(boolean entrada) {
        int actual = dentro.get();
        if (entrada && actual >= config.aforo()) return "aforo completo";
        if (!entrada && actual <= 0) return "nadie dentro";
        if (inseguro) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return "operación interrumpida";
            }
        }
        dentro.addAndGet(entrada ? 1 : -1);
        return "";
    }

    private synchronized String registrarLatido(int puerta) throws IOException {
        ultimoLatido[puerta] = System.nanoTime();
        if (!activas[puerta]) cambiarEstado(puerta, true);
        return "";
    }

    private synchronized void vigilar() {
        long ahora = System.nanoTime();
        for (int puerta = 1; puerta <= 3; puerta++) {
            if (activas[puerta] && ahora - ultimoLatido[puerta] >= TimeUnit.SECONDS.toNanos(6)) {
                try {
                    cambiarEstado(puerta, false);
                } catch (IOException error) {
                    System.err.println(error.getMessage());
                }
            }
        }
    }

    private void cambiarEstado(int puerta, boolean activa) throws IOException {
        String detalle = "P" + puerta + " " + (activa ? "ACTIVA" : "CAIDA");
        bitacora.evento("PUERTA", detalle);
        activas[puerta] = activa;
        System.out.println(detalle);
    }

    private synchronized String estadoPuertas() {
        var estados = new StringJoiner(", ");
        for (int puerta = 1; puerta <= 3; puerta++) {
            estados.add("P" + puerta + "=" + (activas[puerta] ? "ACTIVA" : "CAIDA"));
        }
        return estados.toString();
    }
}
