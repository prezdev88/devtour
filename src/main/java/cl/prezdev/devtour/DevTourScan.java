package cl.prezdev.devtour;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DevTourScan {
    String value();
}