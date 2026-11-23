import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

record Configuracion(String host, int puerto, int aforo, String token) {
    static Configuracion leer() throws IOException {
        var propiedades = new Properties();
        try (var entrada = Files.newBufferedReader(Path.of("config.properties"))) {
            propiedades.load(entrada);
        }
        return new Configuracion(propiedades.getProperty("host"),
            Integer.parseInt(propiedades.getProperty("puerto")),
            Integer.parseInt(propiedades.getProperty("aforo")), propiedades.getProperty("token"));
    }
}

class Bitacora {
    private final int id;
    private final Path archivo;
    private long reloj;

    Bitacora(int id) throws IOException {
        this.id = id;
        Files.createDirectories(Path.of("logs"));
        archivo = Path.of("logs", (id == 0 ? "coordinador" : "P" + id) + ".log");
        if (Files.exists(archivo)) {
            try (var lineas = Files.lines(archivo)) {
                reloj = lineas.mapToLong(linea -> Long.parseLong(linea.split(";", 2)[0])).max().orElse(0);
            }
        }
    }

    synchronized long evento(String evento, String detalle) throws IOException {
        long marca = ++reloj;
        Files.writeString(archivo, marca + ";" + id + ";" + evento + ";" + detalle + "\n",
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        return marca;
    }

    synchronized void recibir(long marca, String detalle) throws IOException {
        reloj = Math.max(reloj, marca);
        evento("RECIBIR", detalle);
    }
}

record Respuesta(String resultado, String motivo, int dentro, int aforo, String puertas, long reloj) {
    String linea() {
        return String.join("|", resultado, motivo, Integer.toString(dentro), Integer.toString(aforo),
            puertas, Long.toString(reloj));
    }

    static Respuesta leer(String linea) throws IOException {
        if (linea == null) throw new EOFException("El coordinador cerró la conexión");
        String[] partes = linea.split("\\|", -1);
        if (partes.length != 6) throw new IOException("Respuesta inválida");
        return new Respuesta(partes[0], partes[1], Integer.parseInt(partes[2]),
            Integer.parseInt(partes[3]), partes[4], Long.parseLong(partes[5]));
    }
}

class Protocolo {
    static BufferedReader lector(Socket socket) throws IOException {
        return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    }

    static BufferedWriter escritor(Socket socket) throws IOException {
        return new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    static void enviar(BufferedWriter salida, String linea) throws IOException {
        salida.write(linea);
        salida.newLine();
        salida.flush();
    }

    static Respuesta solicitar(Configuracion config, String operacion, int puerta, String token,
                              Bitacora bitacora) throws IOException {
        String detalle = operacion + " P" + puerta + " " + java.util.UUID.randomUUID();
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress(config.host(), config.puerto()), 1500);
            socket.setSoTimeout(3000);
            long marca = bitacora.evento("ENVIAR", detalle);
            enviar(escritor(socket), String.join("|", operacion, Integer.toString(puerta),
                token, Long.toString(marca), detalle.substring(detalle.lastIndexOf(' ') + 1)));
            Respuesta respuesta = Respuesta.leer(lector(socket).readLine());
            bitacora.recibir(respuesta.reloj(), detalle + " " + respuesta.resultado());
            return respuesta;
        } catch (IOException error) {
            bitacora.evento("ERROR", detalle + " " + error.getClass().getSimpleName());
            throw error;
        }
    }
}
