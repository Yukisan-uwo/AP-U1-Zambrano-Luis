import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class OrdenTotal {
    private record Evento(long reloj, int proceso, String linea) {}

    public static void main(String[] args) throws IOException {
        var eventos = new ArrayList<Evento>();
        for (String proceso : List.of("coordinador", "P1", "P2", "P3")) {
            for (String linea : Files.readAllLines(Path.of("logs", proceso + ".log"))) {
                String[] campos = linea.split(";", 4);
                eventos.add(new Evento(Long.parseLong(campos[0]), Integer.parseInt(campos[1]), linea));
            }
        }
        eventos.sort(Comparator.comparingLong(Evento::reloj).thenComparingInt(Evento::proceso));
        Path salida = Path.of("logs", "orden_total.log");
        Files.write(salida, eventos.stream().map(Evento::linea).toList());
        System.out.println("Eventos ordenados: " + eventos.size() + " | " + salida);
    }
}

