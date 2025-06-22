package cl.prezdev.devtour;

import java.lang.reflect.Method;
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

    private DevTourInspector() {
    }

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

        Map<Integer, DevTourEntry> entries = new TreeMap<>();

        Reflections reflections = new Reflections(new ConfigurationBuilder()
                .forPackage(basePackage)
                .addScanners(Scanners.TypesAnnotated, Scanners.MethodsAnnotated));

        addClassLevelEntries(reflections, entries);
        addMethodLevelEntries(reflections, entries);

        return entries.values().stream()
                .map(DevTourEntry::format)
                .toList();
    }

    private static void addClassLevelEntries(Reflections reflections, Map<Integer, DevTourEntry> entries) {
        for (Class<?> annotatedClass : reflections.getTypesAnnotatedWith(DevTour.class)) {
            DevTour annotation = annotatedClass.getAnnotation(DevTour.class);
            DevTourEntry entry = new DevTourEntry(
                    annotation.order(),
                    annotatedClass.getSimpleName(),
                    annotation.description(),
                    false);
            entries.put(entry.getOrder(), entry);
        }
    }

    private static void addMethodLevelEntries(Reflections reflections, Map<Integer, DevTourEntry> entries) {
        for (Method method : reflections.getMethodsAnnotatedWith(DevTour.class)) {
            DevTour annotation = method.getAnnotation(DevTour.class);
            String methodFullName = method.getDeclaringClass().getSimpleName() + "." + method.getName() + "()";
            DevTourEntry entry = new DevTourEntry(
                    annotation.order(),
                    methodFullName,
                    annotation.description(),
                    true);
            entries.put(entry.getOrder(), entry);
        }
    }

    private static String tryFindPackageFromAnnotation() {
        Reflections reflections = new Reflections(new ConfigurationBuilder()
                .forPackages("")
                .addScanners(Scanners.TypesAnnotated));

        return reflections.getTypesAnnotatedWith(DevTourScan.class).stream()
                .findFirst()
                .map(annotatedClass -> annotatedClass.getAnnotation(DevTourScan.class).value())
                .orElse(null);
    }

    private static void printBanner() {
        String version = readVersionFromManifest();
        if (logger.isLoggable(Level.INFO)) {
            logger.info(String.format("======= 🧭 DEV TOUR v%s: YOUR GUIDED RIDE THROUGH THE CODEBASE =======", version));
        }
    }

    private static String readVersionFromManifest() {
        try {
            Package pkg = DevTourInspector.class.getPackage();
            return pkg != null ? pkg.getImplementationVersion() : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static void printFooter() {
        logger.info("===================================================================");
    }
}
