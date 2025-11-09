# DevTour para IntelliJ

Plugin que replica la experiencia del panel DevTour de VS Code dentro de los IDEs JetBrains. Permite crear recorridos guiados (`.devtour/devtour.json`), agregar pasos directamente desde el editor y navegar entre ellos con atajos de teclado.

## Características
- Panel dedicado en la barra lateral con todas las DevTours y sus pasos.
- Acciones para crear/seleccionar tours, agregar pasos, eliminarlos y actualizar la configuración.
- Sesión de reproducción con inicio, siguiente/anterior y detención, resaltando el paso activo en el editor con un icono en el gutter.
- Apertura rápida del archivo `.devtour/devtour.json` para ediciones manuales.
- Sincronización automática cuando el archivo cambia en disco.

## Requisitos
- JDK 17.

## Cómo ejecutar el plugin
```bash
cd intellij
./gradlew runIde
```

También puedes arrancar con menos ruido de logs usando:
```bash
cd intellij
./gradlew runIde -q
```

Para generar el paquete instalable (equivalente al VSIX):
```bash
cd intellij
./gradlew buildPlugin
# El zip queda en build/distributions/
```

El comando `runIde` inicia una instancia de IntelliJ Community con el plugin cargado para pruebas locales.
El wrapper incluido usa Gradle 8.10, por lo que no necesitas instalar Gradle en el sistema.

## Uso rápido
- `DevTour: Add Step` (`Ctrl+D Ctrl+A`): agrega la línea actual como paso. Permite elegir o crear DevTour.
- `DevTour: Start/Next/Previous/Stop`: controla la reproducción del tour activo.
- `DevTour: Create/Select Tour`: administra los recorridos disponibles.
- `DevTour: Open Config`: abre `.devtour/devtour.json`.
- `DevTour: Delete Step`: disponible desde el panel al seleccionar un paso.

Todos los pasos se almacenan en `.devtour/devtour.json` utilizando la misma estructura que la extensión original de VS Code, por lo que puedes compartir el archivo entre ambos editores.
