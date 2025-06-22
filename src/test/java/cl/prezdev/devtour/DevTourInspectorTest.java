package cl.prezdev.devtour;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

import static org.junit.jupiter.api.Assertions.*;

@DevTourScan("cl.prezdev.devtour.fake")
class DevTourInspectorTest {

    @Test
    void shouldReturnFormattedEntriesFromFakePackage() {
        // given
        String basePackage = "cl.prezdev.devtour.fake";

        // when
        List<String> output = DevTourInspector.analyze(basePackage);

        // then
        assertEquals(2, output.size());
        assertTrue(output.get(0).contains("🧱 FakeComponent"));  // class
        assertTrue(output.get(1).contains("🔧 FakeComponent.initialize()")); // method
    }

    @Test
    void shouldPrintFormattedOutputToConsole() {
        // Capturar la salida del logger
        ByteArrayOutputStream logCapture = new ByteArrayOutputStream();
        Logger logger = Logger.getLogger(DevTourInspector.class.getName());
        logger.setUseParentHandlers(false); // Desactiva handlers anteriores

        Handler testHandler = new StreamHandler(logCapture, new SimpleFormatter()) {
            @Override
            public synchronized void publish(LogRecord record) {
                super.publish(record);
                flush(); // Importante: forzar escritura inmediata
            }
        };

        logger.addHandler(testHandler);
        logger.setLevel(Level.INFO);

        // Ejecutar el método
        DevTourInspector.analyzeAndPrint();

        // Convertir output a texto
        String output = logCapture.toString(StandardCharsets.UTF_8);

        // Verificaciones
        assertTrue(output.contains("🧱 FakeComponent"));
        assertTrue(output.contains("🔧 FakeComponent.initialize()"));
        assertTrue(output.contains("DEV TOUR v"));
    }
}
