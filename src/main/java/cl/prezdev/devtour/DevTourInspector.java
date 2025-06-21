package cl.prezdev.devtour;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;

public class DevTourInspector {

    private static final Logger logger = Logger.getLogger(DevTourInspector.class.getName());

    private DevTourInspector() {}

    public static void analyzeAndPrint() {
        String basePackage = tryFindPackageFromAnnotation();
        if (basePackage == null) {
            logger.severe("❌ No class annotated with @DevTourScan was found. Cannot continue.");
            return;
        }

        printBanner();
        analyze(basePackage).forEach(logger::info);
        printFooter();
    }

    public static List<String> analyze(String basePackage) {
        if (logger.isLoggable(Level.INFO)) {
            logger.info(String.format("🔍 Analyzing package: %s", basePackage));
        }
        
        Map<Integer, String> flowMap = new TreeMap<>();

        Reflections reflections = new Reflections(new ConfigurationBuilder()
                .forPackage(basePackage)
                .addScanners(Scanners.TypesAnnotated, Scanners.MethodsAnnotated));

        for (Class<?> clazz : reflections.getTypesAnnotatedWith(DevTour.class)) {
            DevTour annotation = clazz.getAnnotation(DevTour.class);
            flowMap.put(annotation.order(), formatEntry(clazz.getSimpleName(), false, annotation.description()));
        }

        for (Method method : reflections.getMethodsAnnotatedWith(DevTour.class)) {
            DevTour annotation = method.getAnnotation(DevTour.class);
            String fullName = method.getDeclaringClass().getSimpleName() + "." + method.getName() + "()";
            flowMap.put(annotation.order(), formatEntry(fullName, true, annotation.description()));
        }

        return new ArrayList<>(flowMap.values());
    }

    private static String tryFindPackageFromAnnotation() {
        Reflections reflections = new Reflections(new ConfigurationBuilder()
                .forPackages("")
                .addScanners(Scanners.TypesAnnotated));

        return reflections.getTypesAnnotatedWith(DevTourScan.class).stream()
                .findFirst()
                .map(cls -> cls.getAnnotation(DevTourScan.class).value())
                .orElse(null);
    }

    private static String formatEntry(String name, boolean isMethod, String description) {
        String icon = isMethod ? "🔧 " : "🧱 ";
        return icon + name + (description.isBlank() ? "" : "  // " + description);
    }

    private static void printBanner() {
        logger.info("======= 🧭 DEV TOUR: YOUR GUIDED RIDE THROUGH THE CODEBASE =======");
    }

    private static void printFooter() {
        logger.info("===================================================================");
    }
}
