package cl.prezdev.devtour.fake;

import cl.prezdev.devtour.DevTour;

@DevTour(order = 1, description = "Fake class for testing")
public class FakeComponent {

    @DevTour(order = 2, description = "Fake method for testing")
    public void initialize() {
        // nothing
    }
}
