# AforoSeguro

AforoSeguro lleva el conteo de personas de tres puertas. El coordinador autoriza las entradas y registra las salidas. Cada puerta envía latidos para que el coordinador pueda detectar cuándo se desconecta.

## Compilar y ejecutar

Se necesita Java 21. Abre PowerShell en la raíz del proyecto y compila:

~~~powershell
javac -encoding UTF-8 -d build src/*.java
~~~

Arranca primero el coordinador:

~~~powershell
java -cp build Coordinador
~~~

Despues se abren tres terminales en la misma carpeta y ejecuta un comando por terminal:

~~~powershell
java -cp build Puerta 1
java -cp build Puerta 2
java -cp build Puerta 3
~~~

En cada puerta podemos escribir **entrar**, **salir**, **estado** o **fin**. El aforo, el token, el host y el puerto se leen de **config.properties**. Para probar un rechazo por token, cierra P2 e inicia esa puerta con **java -cp build Puerta 2 incorrecto**.

Para probar 30 entradas simultáneas, cierra las puertas, arranca un coordinador nuevo y ejecuta el siguiente comando desde otra terminal:

~~~powershell
java -cp build PruebaCarga
~~~

Para repetir la carga en modo inseguro, detén el coordinador y arráncalo con **java -cp build Coordinador --inseguro**. Cuando termines las pruebas, detén los procesos y genera el registro combinado:

~~~powershell
java -cp build OrdenTotal
~~~

## Formato de los mensajes

Cada mensaje TCP es una línea UTF-8. La barra vertical separa los campos.

~~~text
OPERACION|PUERTA|TOKEN|LAMPORT|UUID
RESULTADO|MOTIVO|DENTRO|AFORO|ESTADO_PUERTAS|LAMPORT
~~~

Las operaciones son **ENTRAR**, **SALIR**, **ESTADO** y **LATIDO**. La primera línea es la solicitud y la segunda es la respuesta. El resultado puede ser **PERMITIDA** o **RECHAZADA**. El UUID de la solicitud permite seguir su envío y recepción en las bitácoras.

## Compilar el informe

El archivo principal es **docs/informe.tex**. Se necesita pdfLaTeX con los paquetes **fontenc**, **inputenc**, **geometry** y **tabularx**. Desde la raíz del proyecto, ejecuta:

~~~powershell
Push-Location docs
pdflatex -interaction=nonstopmode -halt-on-error informe.tex
pdflatex -interaction=nonstopmode -halt-on-error informe.tex
Pop-Location
~~~

El archivo generado queda en **docs/informe.pdf**.

## Código reutilizado

No se reutilizó código.
