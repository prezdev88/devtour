package cl.prezdev.devtour;

public class DevTourEntry {

    private final int order;
    private final String name;
    private final String description;
    private final boolean method;

    public DevTourEntry(int order, String name, String description, boolean method) {
        this.order = order;
        this.name = name;
        this.description = description;
        this.method = method;
    }

    public int getOrder() {
        return order;
    }

    public String format() {
        String icon = method ? "🔧 " : "🧱 ";
        return icon + name + (description.isBlank() ? "" : "  // " + description);
    }
}
